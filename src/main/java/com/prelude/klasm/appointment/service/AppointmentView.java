package com.prelude.klasm.appointment.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public record AppointmentView(
        UUID id,
        AppointmentStatus status,
        UUID dealershipId,
        ZoneId timeZone,
        UUID serviceTypeId,
        ServiceBay serviceBay,
        Technician technician,
        UUID customerId,
        UUID vehicleId,
        Instant startTime,
        Instant endTime,
        Instant cancelledAt,
        String cancellationReason) {

    public record ServiceBay(UUID id, String code) {}

    public record Technician(UUID id, String fullName) {}
}
