package com.prelude.klasm.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prelude.klasm.appointment.repository.AppointmentConflictException;
import com.prelude.klasm.appointment.repository.AppointmentInsertRepository;
import com.prelude.klasm.appointment.repository.AppointmentResourceLockRepository;
import com.prelude.klasm.appointment.repository.NewAppointment;
import com.prelude.klasm.availability.service.AvailabilityService;
import com.prelude.klasm.availability.service.BayCandidate;
import com.prelude.klasm.availability.service.BookingProperties;
import com.prelude.klasm.availability.service.BookingWindowPolicy;
import com.prelude.klasm.availability.service.TechnicianCandidate;
import com.prelude.klasm.catalog.service.ServiceTypeService;
import com.prelude.klasm.catalog.service.ServiceTypeView;
import com.prelude.klasm.common.error.ApiException;
import com.prelude.klasm.common.error.ErrorCode;
import com.prelude.klasm.customer.service.CustomerService;
import com.prelude.klasm.dealership.service.DealershipService;
import com.prelude.klasm.dealership.service.DealershipView;
import com.prelude.klasm.dealership.service.ServiceBayView;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/** Retry and error mapping of the booking flow, with the database replaced by mocks. */
class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant START = Instant.parse("2030-01-07T09:00:00Z"); // Monday

    private final UUID dealershipId = UUID.randomUUID();
    private final UUID serviceTypeId = UUID.randomUUID();
    private final BayCandidate bay1 = new BayCandidate(UUID.randomUUID(), "BAY-01");
    private final BayCandidate bay2 = new BayCandidate(UUID.randomUUID(), "BAY-02");
    private final BayCandidate bay3 = new BayCandidate(UUID.randomUUID(), "BAY-03");
    private final TechnicianCandidate tech1 = new TechnicianCandidate(UUID.randomUUID(), "T1", 0);
    private final TechnicianCandidate tech2 = new TechnicianCandidate(UUID.randomUUID(), "T2", 0);

    private final DealershipService dealershipService = mock(DealershipService.class);
    private final ServiceTypeService serviceTypeService = mock(ServiceTypeService.class);
    private final AvailabilityService availabilityService = mock(AvailabilityService.class);
    private final CustomerService customerService = mock(CustomerService.class);
    private final AppointmentInsertRepository insertRepository = mock(AppointmentInsertRepository.class);
    private final AppointmentResourceLockRepository resourceLockRepository =
            mock(AppointmentResourceLockRepository.class);
    private final AppointmentQueryService queryService = mock(AppointmentQueryService.class);
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        bookingService = new BookingService(
                dealershipService,
                serviceTypeService,
                availabilityService,
                customerService,
                insertRepository,
                resourceLockRepository,
                queryService,
                new BookingWindowPolicy(clock, new BookingProperties(Duration.ofMinutes(60), Duration.ofDays(90))),
                transactionManager,
                clock);

        when(dealershipService.getActive(dealershipId))
                .thenReturn(new DealershipView(
                        dealershipId,
                        "Test",
                        ZoneId.of("UTC"),
                        List.of(new DealershipView.WorkingHours(
                                DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0)))));
        when(serviceTypeService.getActive(serviceTypeId))
                .thenReturn(new ServiceTypeView(serviceTypeId, "OIL_CHANGE", "Oil change", 60));
        when(dealershipService.hasQualifiedTechnician(dealershipId, serviceTypeId))
                .thenReturn(true);
        when(availabilityService.freeBays(dealershipId, START, START.plusSeconds(3600)))
                .thenReturn(List.of(bay1, bay2, bay3));
        when(availabilityService.freeTechnicians(
                        eq(dealershipId), eq(serviceTypeId), eq(START), eq(START.plusSeconds(3600)), any()))
                .thenReturn(List.of(tech1, tech2));
        when(customerService.resolveCustomer(anyString(), anyString(), any())).thenReturn(UUID.randomUUID());
        when(customerService.resolveVehicle(anyString(), any(), any(), any())).thenReturn(UUID.randomUUID());
    }

    @Test
    void assignsFirstBayAndTechnician() {
        BookingResult result = bookingService.book(command(START, null, null));
        AppointmentView view = result.appointment();

        assertThat(result.created()).isTrue();
        assertThat(view.serviceBay().id()).isEqualTo(bay1.id());
        assertThat(view.technician().id()).isEqualTo(tech1.id());
        assertThat(view.endTime()).isEqualTo(START.plusSeconds(3600));
        assertThat(view.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(resourceLockRepository).lockResources(bay1.id(), tech1.id(), "1HGCM82633A004352");
    }

    @Test
    void retriesWithNextBayAfterBayConflict() {
        failInsertsWith(AppointmentConflictException.Kind.SERVICE_BAY, 1);

        AppointmentView view = bookingService.book(command(START, null, null)).appointment();

        assertThat(view.serviceBay().id()).isEqualTo(bay2.id());
        assertThat(view.technician().id()).isEqualTo(tech1.id());
    }

    @Test
    void retriesWithNextTechnicianAfterTechnicianConflict() {
        failInsertsWith(AppointmentConflictException.Kind.TECHNICIAN, 1);

        AppointmentView view = bookingService.book(command(START, null, null)).appointment();

        assertThat(view.serviceBay().id()).isEqualTo(bay1.id());
        assertThat(view.technician().id()).isEqualTo(tech2.id());
    }

    @Test
    void givesUpAfterThreeAttempts() {
        failInsertsWith(AppointmentConflictException.Kind.SERVICE_BAY, 3);

        assertError(() -> bookingService.book(command(START, null, null)), ErrorCode.NO_AVAILABILITY);
        verify(insertRepository, times(BookingService.MAX_ATTEMPTS)).insertConfirmed(any());
    }

    @Test
    void reportsNoAvailabilityWhenNoCandidateIsFree() {
        when(availabilityService.freeBays(any(), any(), any())).thenReturn(List.of());

        assertError(() -> bookingService.book(command(START, null, null)), ErrorCode.NO_AVAILABILITY);
        verify(insertRepository, times(0)).insertConfirmed(any());
    }

    @Test
    void vehicleConflictIsNotRetried() {
        failInsertsWith(AppointmentConflictException.Kind.VEHICLE, 1);

        assertError(() -> bookingService.book(command(START, null, null)), ErrorCode.VEHICLE_ALREADY_BOOKED);
        verify(insertRepository, times(1)).insertConfirmed(any());
    }

    @Test
    void conflictOnExplicitBayIsResourceConflict() {
        when(dealershipService.requireServiceBay(dealershipId, bay1.id()))
                .thenReturn(new ServiceBayView(bay1.id(), bay1.code()));
        failInsertsWith(AppointmentConflictException.Kind.SERVICE_BAY, 1);

        assertError(() -> bookingService.book(command(START, bay1.id(), null)), ErrorCode.RESOURCE_CONFLICT);
        verify(insertRepository, times(1)).insertConfirmed(any());
    }

    @Test
    void rejectsDealershipWithoutQualifiedTechnician() {
        when(dealershipService.hasQualifiedTechnician(dealershipId, serviceTypeId))
                .thenReturn(false);

        assertError(() -> bookingService.book(command(START, null, null)), ErrorCode.TECHNICIAN_NOT_QUALIFIED);
    }

    @Test
    void rejectsStartOutsideWorkingHours() {
        Instant evening = Instant.parse("2030-01-07T16:30:00Z");

        assertError(() -> bookingService.book(command(evening, null, null)), ErrorCode.OUTSIDE_WORKING_HOURS);
    }

    @Test
    void rejectsStartInsideMinimumLeadTime() {
        assertError(
                () -> bookingService.book(command(NOW.plusSeconds(1800), null, null)),
                ErrorCode.INVALID_BOOKING_WINDOW);
    }

    @Test
    void storesCalculatedEndTimeAndClockCreationTime() {
        bookingService.book(command(START, null, null));

        ArgumentCaptor<NewAppointment> inserted = ArgumentCaptor.forClass(NewAppointment.class);
        verify(insertRepository).insertConfirmed(inserted.capture());
        assertThat(inserted.getValue().endTime()).isEqualTo(START.plusSeconds(3600));
        assertThat(inserted.getValue().createdAt()).isEqualTo(NOW);
    }

    @Test
    void replaysStoredBookingForSameKeyAndBodyWithoutInserting() {
        BookingCommand command = command(START, null, null, "key-1");
        AppointmentView stored = storedView();
        when(queryService.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(new AppointmentQueryService.StoredBooking(stored, RequestHash.of(command))));

        BookingResult result = bookingService.book(command);

        assertThat(result.created()).isFalse();
        assertThat(result.appointment()).isEqualTo(stored);
        verify(insertRepository, never()).insertConfirmed(any());
    }

    @Test
    void rejectsSameKeyWithDifferentBody() {
        when(queryService.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.of(new AppointmentQueryService.StoredBooking(storedView(), "other-hash")));

        assertError(() -> bookingService.book(command(START, null, null, "key-1")), ErrorCode.IDEMPOTENCY_KEY_MISMATCH);
    }

    @Test
    void returnsWinnerWhenConcurrentRequestWithSameKeyInsertedFirst() {
        BookingCommand command = command(START, null, null, "key-1");
        AppointmentView stored = storedView();
        when(queryService.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new AppointmentQueryService.StoredBooking(stored, RequestHash.of(command))));
        failInsertsWith(AppointmentConflictException.Kind.IDEMPOTENCY_KEY, 1);

        BookingResult result = bookingService.book(command);

        assertThat(result.created()).isFalse();
        assertThat(result.appointment()).isEqualTo(stored);
        verify(insertRepository, times(1)).insertConfirmed(any());
    }

    @Test
    void storesIdempotencyKeyAndRequestHash() {
        BookingCommand command = command(START, null, null, "key-1");
        when(queryService.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());

        bookingService.book(command);

        ArgumentCaptor<NewAppointment> inserted = ArgumentCaptor.forClass(NewAppointment.class);
        verify(insertRepository).insertConfirmed(inserted.capture());
        assertThat(inserted.getValue().idempotencyKey()).isEqualTo("key-1");
        assertThat(inserted.getValue().requestHash()).isEqualTo(RequestHash.of(command));
    }

    private AppointmentView storedView() {
        return new AppointmentView(
                UUID.randomUUID(),
                AppointmentStatus.CONFIRMED,
                dealershipId,
                ZoneId.of("UTC"),
                serviceTypeId,
                new AppointmentView.ServiceBay(bay1.id(), bay1.code()),
                new AppointmentView.Technician(tech1.id(), tech1.fullName()),
                UUID.randomUUID(),
                UUID.randomUUID(),
                START,
                START.plusSeconds(3600),
                null,
                null);
    }

    private void failInsertsWith(AppointmentConflictException.Kind kind, int failures) {
        var stub = doThrow(new AppointmentConflictException(kind, null));
        for (int i = 1; i < failures; i++) {
            stub = stub.doThrow(new AppointmentConflictException(kind, null));
        }
        stub.doNothing().when(insertRepository).insertConfirmed(any());
    }

    private static void assertError(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.code()).isEqualTo(code));
    }

    private BookingCommand command(Instant start, UUID bayId, UUID technicianId) {
        return command(start, bayId, technicianId, null);
    }

    private BookingCommand command(Instant start, UUID bayId, UUID technicianId, String idempotencyKey) {
        return new BookingCommand(
                dealershipId,
                serviceTypeId,
                start,
                "Jane Doe",
                "jane@example.com",
                null,
                "1HGCM82633A004352",
                null,
                null,
                null,
                bayId,
                technicianId,
                null,
                idempotencyKey);
    }
}
