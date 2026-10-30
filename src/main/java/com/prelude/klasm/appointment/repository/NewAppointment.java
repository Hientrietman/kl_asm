package com.prelude.klasm.appointment.repository;

import java.time.Instant;
import java.util.UUID;

/** Values for one appointment insert. */
public record NewAppointment(
        UUID id,
        UUID dealershipId,
        UUID customerId,
        UUID vehicleId,
        UUID technicianId,
        UUID serviceBayId,
        UUID serviceTypeId,
        Instant startTime,
        Instant endTime,
        String idempotencyKey,
        String requestHash,
        String notes,
        Instant createdAt) {}
