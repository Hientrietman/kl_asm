package com.prelude.klasm.appointment.controller;

import com.prelude.klasm.appointment.service.AppointmentView;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Appointment as returned by the API. Instants are in UTC; {@code timeZone} is the dealership's IANA zone. */
public record AppointmentResponse(
        UUID id,
        String status,
        UUID dealershipId,
        String timeZone,
        UUID serviceTypeId,
        ServiceBay serviceBay,
        Technician technician,
        UUID customerId,
        UUID vehicleId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        OffsetDateTime cancelledAt,
        String cancellationReason) {

    public record ServiceBay(UUID id, String code) {}

    public record Technician(UUID id, String fullName) {}

    static AppointmentResponse from(AppointmentView view) {
        return new AppointmentResponse(
                view.id(),
                view.status().name(),
                view.dealershipId(),
                view.timeZone().getId(),
                view.serviceTypeId(),
                new ServiceBay(view.serviceBay().id(), view.serviceBay().code()),
                new Technician(view.technician().id(), view.technician().fullName()),
                view.customerId(),
                view.vehicleId(),
                utc(view.startTime()),
                utc(view.endTime()),
                utc(view.cancelledAt()),
                view.cancellationReason());
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
