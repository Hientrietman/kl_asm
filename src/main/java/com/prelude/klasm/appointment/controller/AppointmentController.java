package com.prelude.klasm.appointment.controller;

import com.prelude.klasm.appointment.service.BookingCommand;
import com.prelude.klasm.appointment.service.BookingResult;
import com.prelude.klasm.appointment.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
class AppointmentController {

    private final BookingService bookingService;

    AppointmentController(BookingService bookingService) {
        this.bookingService = bookingService;
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
