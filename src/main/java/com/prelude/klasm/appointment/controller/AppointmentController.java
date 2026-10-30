package com.prelude.klasm.appointment.controller;

import com.prelude.klasm.appointment.service.AppointmentView;
import com.prelude.klasm.appointment.service.BookingCommand;
import com.prelude.klasm.appointment.service.BookingService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
class AppointmentController {

    private final BookingService bookingService;

    AppointmentController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentView appointment = bookingService.book(toCommand(request));
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + appointment.id()))
                .body(AppointmentResponse.from(appointment));
    }

    private static BookingCommand toCommand(CreateAppointmentRequest request) {
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
                request.notes());
    }
}
