package com.prelude.klasm.appointment.service;

import com.prelude.klasm.appointment.repository.AppointmentConflictException;
import com.prelude.klasm.appointment.repository.AppointmentInsertRepository;
import com.prelude.klasm.appointment.repository.AppointmentResourceLockRepository;
import com.prelude.klasm.appointment.repository.NewAppointment;
import com.prelude.klasm.availability.service.AvailabilityService;
import com.prelude.klasm.availability.service.BayCandidate;
import com.prelude.klasm.availability.service.BookingWindowPolicy;
import com.prelude.klasm.availability.service.TechnicianCandidate;
import com.prelude.klasm.catalog.service.ServiceTypeService;
import com.prelude.klasm.catalog.service.ServiceTypeView;
import com.prelude.klasm.common.error.ApiException;
import com.prelude.klasm.common.error.ErrorCode;
import com.prelude.klasm.common.logging.LogMasking;
import com.prelude.klasm.customer.service.CustomerService;
import com.prelude.klasm.dealership.service.DealershipService;
import com.prelude.klasm.dealership.service.DealershipView;
import com.prelude.klasm.dealership.service.ServiceBayView;
import com.prelude.klasm.dealership.service.TechnicianView;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Booking orchestration (FR-1, FR-2, FR-3). Deliberately not {@code @Transactional}: a failed INSERT aborts the
 * PostgreSQL transaction, so each attempt runs in its own short transaction and a retry can follow (FR-2.8).
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    static final int MAX_ATTEMPTS = 3;
    static final int MAX_ALTERNATIVES = 3;

    private final DealershipService dealershipService;
    private final ServiceTypeService serviceTypeService;
    private final AvailabilityService availabilityService;
    private final CustomerService customerService;
    private final AppointmentInsertRepository insertRepository;
    private final AppointmentResourceLockRepository resourceLockRepository;
    private final AppointmentQueryService queryService;
    private final BookingWindowPolicy bookingWindowPolicy;
    private final TransactionTemplate attemptTransaction;
    private final BookingMetrics metrics;
    private final Clock clock;

    public BookingService(
            DealershipService dealershipService,
            ServiceTypeService serviceTypeService,
            AvailabilityService availabilityService,
            CustomerService customerService,
            AppointmentInsertRepository insertRepository,
            AppointmentResourceLockRepository resourceLockRepository,
            AppointmentQueryService queryService,
            BookingWindowPolicy bookingWindowPolicy,
            PlatformTransactionManager transactionManager,
            BookingMetrics metrics,
            Clock clock) {
        this.dealershipService = dealershipService;
        this.serviceTypeService = serviceTypeService;
        this.availabilityService = availabilityService;
        this.customerService = customerService;
        this.insertRepository = insertRepository;
        this.resourceLockRepository = resourceLockRepository;
        this.queryService = queryService;
        this.bookingWindowPolicy = bookingWindowPolicy;
        this.attemptTransaction = new TransactionTemplate(transactionManager);
        this.attemptTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.metrics = metrics;
        this.clock = clock;
    }

    public BookingResult book(BookingCommand command) {
        Timer.Sample sample = Timer.start();
        try {
            BookingResult result = bookUnmetered(command);
            AppointmentView appointment = result.appointment();
            if (result.created()) {
                metrics.booked();
            }
            log.info(
                    "Booking {} appointmentId={} dealershipId={} serviceTypeId={} serviceBayId={} technicianId={}"
                            + " vinSuffix={}",
                    result.created() ? "CREATED" : "REPLAYED",
                    appointment.id(),
                    appointment.dealershipId(),
                    appointment.serviceTypeId(),
                    appointment.serviceBay().id(),
                    appointment.technician().id(),
                    LogMasking.vinSuffix(command.vin()));
            return result;
        } catch (ApiException rejected) {
            metrics.rejected(rejected.code());
            log.info(
                    "Booking rejected code={} dealershipId={} serviceTypeId={} vinSuffix={}",
                    rejected.code(),
                    command.dealershipId(),
                    command.serviceTypeId(),
                    LogMasking.vinSuffix(command.vin()));
            throw rejected;
        } finally {
            sample.stop(metrics.duration());
        }
    }

    private BookingResult bookUnmetered(BookingCommand command) {
        String requestHash = command.idempotencyKey() == null ? null : RequestHash.of(command);
        // Checked first: a retry must return the original result even if the time rules would now reject it
        Optional<BookingResult> replay = replay(command.idempotencyKey(), requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        DealershipView dealership = dealershipService.getActive(command.dealershipId());
        ServiceTypeView serviceType = serviceTypeService.getActive(command.serviceTypeId());
        Instant start = command.startTime();
        Instant end = start.plus(Duration.ofMinutes(serviceType.durationMinutes()));

        bookingWindowPolicy.check(start);
        if (!dealership.isOpenDuring(start, end)) {
            throw new ApiException(
                    ErrorCode.OUTSIDE_WORKING_HOURS,
                    "The appointment must lie within the dealership's working hours of a single day.");
        }

        AssignmentPlan plan = plan(command, dealership, start, end);
        AppointmentConflictException.Kind lastConflict = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS && plan.hasCandidate(); attempt++) {
            if (attempt > 1) {
                metrics.retried();
            }
            BayCandidate bay = plan.bay();
            TechnicianCandidate technician = plan.technician();
            try {
                Inserted inserted = insert(command, requestHash, start, end, bay, technician);
                return new BookingResult(
                        new AppointmentView(
                                inserted.id(),
                                AppointmentStatus.CONFIRMED,
                                dealership.id(),
                                dealership.timeZone(),
                                serviceType.id(),
                                new AppointmentView.ServiceBay(bay.id(), bay.code()),
                                new AppointmentView.Technician(technician.id(), technician.fullName()),
                                inserted.customerId(),
                                inserted.vehicleId(),
                                start,
                                end,
                                null,
                                null),
                        true);
            } catch (AppointmentConflictException conflict) {
                // A concurrent request with the same key may have won the race (FR-3.4)
                replay = replay(command.idempotencyKey(), requestHash);
                if (replay.isPresent()) {
                    return replay.get();
                }
                lastConflict = conflict.kind();
                switch (conflict.kind()) {
                    case VEHICLE ->
                        throw conflict(
                                ErrorCode.VEHICLE_ALREADY_BOOKED,
                                "The vehicle already has a confirmed appointment that overlaps this time.",
                                "VEHICLE");
                    case SERVICE_BAY -> {
                        if (command.serviceBayId() != null) {
                            throw resourceConflict("SERVICE_BAY");
                        }
                        plan.skipBay();
                    }
                    case TECHNICIAN -> {
                        if (command.technicianId() != null) {
                            throw resourceConflict("TECHNICIAN");
                        }
                        plan.skipTechnician();
                    }
                    case IDEMPOTENCY_KEY -> throw conflict;
                }
            }
        }
        String resource = lastConflict == AppointmentConflictException.Kind.TECHNICIAN
                        || (lastConflict == null && !plan.baysExhausted())
                ? "TECHNICIAN"
                : "SERVICE_BAY";
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("resource", resource);
        if (command.serviceBayId() == null && command.technicianId() == null) {
            conflict.put(
                    "alternatives",
                    availabilityService.alternativeStarts(
                            dealership, serviceType.id(), serviceType.durationMinutes(), start, MAX_ALTERNATIVES));
        }
        throw new ApiException(
                ErrorCode.NO_AVAILABILITY,
                "No service bay and qualified technician are both free for the requested time.",
                Map.of("conflict", conflict));
    }

    private AssignmentPlan plan(BookingCommand command, DealershipView dealership, Instant start, Instant end) {
        List<BayCandidate> bays;
        if (command.serviceBayId() != null) {
            ServiceBayView bay = dealershipService.requireServiceBay(dealership.id(), command.serviceBayId());
            bays = List.of(new BayCandidate(bay.id(), bay.code()));
        } else {
            bays = availabilityService.freeBays(dealership.id(), start, end);
        }

        List<TechnicianCandidate> technicians;
        if (command.technicianId() != null) {
            TechnicianView technician = dealershipService.requireQualifiedTechnician(
                    dealership.id(), command.technicianId(), command.serviceTypeId());
            technicians = List.of(new TechnicianCandidate(technician.id(), technician.fullName(), 0));
        } else {
            if (!dealershipService.hasQualifiedTechnician(dealership.id(), command.serviceTypeId())) {
                throw new ApiException(
                        ErrorCode.TECHNICIAN_NOT_QUALIFIED,
                        "No technician at this dealership is qualified for the service type.");
            }
            technicians = availabilityService.freeTechnicians(
                    dealership.id(), command.serviceTypeId(), start, end, dealership.timeZone());
        }
        return new AssignmentPlan(bays, technicians);
    }

    private Optional<BookingResult> replay(String idempotencyKey, String requestHash) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return queryService.findByIdempotencyKey(idempotencyKey).map(stored -> {
            if (!stored.requestHash().equals(requestHash)) {
                throw new ApiException(
                        ErrorCode.IDEMPOTENCY_KEY_MISMATCH,
                        "The Idempotency-Key was already used with a different request.");
            }
            return new BookingResult(stored.appointment(), false);
        });
    }

    private Inserted insert(
            BookingCommand command,
            String requestHash,
            Instant start,
            Instant end,
            BayCandidate bay,
            TechnicianCandidate technician) {
        return attemptTransaction.execute(status -> {
            resourceLockRepository.lockResources(bay.id(), technician.id(), command.vin());
            UUID customerId = customerService.resolveCustomer(
                    command.customerFullName(), command.customerEmail(), command.customerPhone());
            UUID vehicleId = customerService.resolveVehicle(
                    command.vin(), command.vehicleMake(), command.vehicleModel(), command.vehicleYear());
            UUID id = UUID.randomUUID();
            insertRepository.insertConfirmed(new NewAppointment(
                    id,
                    command.dealershipId(),
                    customerId,
                    vehicleId,
                    technician.id(),
                    bay.id(),
                    command.serviceTypeId(),
                    start,
                    end,
                    command.idempotencyKey(),
                    requestHash,
                    command.notes(),
                    clock.instant()));
            return new Inserted(id, customerId, vehicleId);
        });
    }

    private static ApiException resourceConflict(String resource) {
        return conflict(
                ErrorCode.RESOURCE_CONFLICT, "The requested resource is already booked for this time.", resource);
    }

    private static ApiException conflict(ErrorCode code, String detail, String resource) {
        return new ApiException(code, detail, Map.of("conflict", Map.of("resource", resource)));
    }

    private record Inserted(UUID id, UUID customerId, UUID vehicleId) {}
}
