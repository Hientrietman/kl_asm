package com.prelude.klasm.appointment.service;

import com.prelude.klasm.appointment.repository.AppointmentEntity;
import com.prelude.klasm.appointment.repository.AppointmentRepository;
import com.prelude.klasm.common.error.ApiException;
import com.prelude.klasm.common.error.ErrorCode;
import com.prelude.klasm.customer.service.CustomerService;
import com.prelude.klasm.dealership.service.DealershipService;
import jakarta.persistence.criteria.Predicate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads appointments and turns them into views, resolving bay codes, names and zones in batches. */
@Service
public class AppointmentQueryService {

    private final AppointmentRepository appointments;
    private final DealershipService dealershipService;
    private final CustomerService customerService;

    public AppointmentQueryService(
            AppointmentRepository appointments, DealershipService dealershipService, CustomerService customerService) {
        this.appointments = appointments;
        this.dealershipService = dealershipService;
        this.customerService = customerService;
    }

    @Transactional(readOnly = true)
    public AppointmentView get(UUID id) {
        return appointments
                .findById(id)
                .map(entity -> toViews(List.of(entity)).get(0))
                .orElseThrow(() -> notFound(id));
    }

    /** FR-4.2: filtered page ordered by start time (then id, so pages are stable). */
    @Transactional(readOnly = true)
    public AppointmentPage list(AppointmentFilter filter) {
        UUID vehicleId = null;
        if (filter.vin() != null) {
            Optional<UUID> vehicle = customerService.findVehicleIdByVin(filter.vin());
            if (vehicle.isEmpty()) {
                return new AppointmentPage(List.of(), filter.page(), filter.size(), 0, 0);
            }
            vehicleId = vehicle.get();
        }
        Page<AppointmentEntity> page = appointments.findAll(
                matching(filter, vehicleId), PageRequest.of(filter.page(), filter.size(), Sort.by("startTime", "id")));
        return new AppointmentPage(
                toViews(page.getContent()),
                filter.page(),
                filter.size(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    /** The stored appointment for an idempotency key, with the request hash it was created from. */
    @Transactional(readOnly = true)
    public Optional<StoredBooking> findByIdempotencyKey(String idempotencyKey) {
        return appointments
                .findByIdempotencyKey(idempotencyKey)
                .map(entity -> new StoredBooking(toViews(List.of(entity)).get(0), entity.getRequestHash()));
    }

    List<AppointmentView> toViews(List<AppointmentEntity> entities) {
        if (entities.isEmpty()) {
            return List.of();
        }
        Map<UUID, ZoneId> zones = dealershipService.timeZones(ids(entities, AppointmentEntity::getDealershipId));
        Map<UUID, String> bayCodes =
                dealershipService.serviceBayCodes(ids(entities, AppointmentEntity::getServiceBayId));
        Map<UUID, String> technicianNames =
                dealershipService.technicianNames(ids(entities, AppointmentEntity::getTechnicianId));
        return entities.stream()
                .map(entity -> new AppointmentView(
                        entity.getId(),
                        entity.getStatus(),
                        entity.getDealershipId(),
                        zones.get(entity.getDealershipId()),
                        entity.getServiceTypeId(),
                        new AppointmentView.ServiceBay(
                                entity.getServiceBayId(), bayCodes.get(entity.getServiceBayId())),
                        new AppointmentView.Technician(
                                entity.getTechnicianId(), technicianNames.get(entity.getTechnicianId())),
                        entity.getCustomerId(),
                        entity.getVehicleId(),
                        entity.getStartTime(),
                        entity.getEndTime(),
                        entity.getCancelledAt(),
                        entity.getCancellationReason()))
                .toList();
    }

    private static Specification<AppointmentEntity> matching(AppointmentFilter filter, UUID vehicleId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.dealershipId() != null) {
                predicates.add(cb.equal(root.get("dealershipId"), filter.dealershipId()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("startTime"), filter.to()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (vehicleId != null) {
                predicates.add(cb.equal(root.get("vehicleId"), vehicleId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static ApiException notFound(UUID id) {
        return new ApiException(ErrorCode.NOT_FOUND, "Appointment " + id + " not found.");
    }

    private static Set<UUID> ids(List<AppointmentEntity> entities, Function<AppointmentEntity, UUID> id) {
        return entities.stream().map(id).collect(Collectors.toSet());
    }

    public record StoredBooking(AppointmentView appointment, String requestHash) {}
}
