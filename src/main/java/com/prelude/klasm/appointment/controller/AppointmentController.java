package com.prelude.klasm.appointment.controller;

import com.prelude.klasm.appointment.service.AppointmentFilter;
import com.prelude.klasm.appointment.service.AppointmentQueryService;
import com.prelude.klasm.appointment.service.AppointmentStatus;
import com.prelude.klasm.appointment.service.BookingCommand;
import com.prelude.klasm.appointment.service.BookingResult;
import com.prelude.klasm.appointment.service.BookingService;
import com.prelude.klasm.appointment.service.CancellationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
class AppointmentController {

    private final BookingService bookingService;
    private final AppointmentQueryService queryService;
    private final CancellationService cancellationService;

    AppointmentController(
            BookingService bookingService,
            AppointmentQueryService queryService,
            CancellationService cancellationService) {
        this.bookingService = bookingService;
        this.queryService = queryService;
        this.cancellationService = cancellationService;
    }

    @GetMapping("/{id}")
    AppointmentResponse get(@PathVariable UUID id) {
        return AppointmentResponse.from(queryService.get(id));
    }

    @GetMapping
    AppointmentPageResponse list(
            @RequestParam(required = false) UUID dealershipId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) String vin,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        AppointmentFilter filter = new AppointmentFilter(
                dealershipId,
                from == null ? null : from.toInstant(),
                to == null ? null : to.toInstant(),
                status,
                vin == null || vin.isBlank() ? null : vin.trim().toUpperCase(Locale.ROOT),
                page,
                size);
        return AppointmentPageResponse.from(queryService.list(filter));
    }

    /** Idempotent: cancelling a cancelled appointment returns its current state (FR-4.3). */
    @PostMapping("/{id}/cancel")
    AppointmentResponse cancel(
            @PathVariable UUID id, @Valid @RequestBody(required = false) CancelAppointmentRequest request) {
        String reason = request == null ? null : request.reason();
        return AppointmentResponse.from(cancellationService.cancel(id, reason));
    }

    /** 201 for a new booking; 200 with the original appointment when an Idempotency-Key is replayed (FR-3.4). */
    @PostMapping
    ResponseEntity<AppointmentResponse> create(
            @Valid @RequestBody CreateAppointmentRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(min = 1, max = 64) String idempotencyKey) {
        BookingResult result = bookingService.book(toCommand(request, idempotencyKey));
        AppointmentResponse body = AppointmentResponse.from(result.appointment());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + body.id()))
                .body(body);
    }

    private static BookingCommand toCommand(CreateAppointmentRequest request, String idempotencyKey) {
        return new BookingCommand(
                request.dealershipId(),
                request.serviceTypeId(),
                request.startTime().toInstant(),
                request.customer().fullName(),
                request.customer().email(),
                request.customer().phone(),
                request.vehicle().vin(),
                request.vehicle().make(),
                request.vehicle().model(),
                request.vehicle().year(),
                request.serviceBayId(),
                request.technicianId(),
                request.notes(),
                idempotencyKey);
    }
}
