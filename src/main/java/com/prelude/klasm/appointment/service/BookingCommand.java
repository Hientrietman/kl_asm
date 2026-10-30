package com.prelude.klasm.appointment.service;

import java.time.Instant;
import java.util.UUID;

/** A normalized, field-validated booking request (FR-1). */
public record BookingCommand(
        UUID dealershipId,
        UUID serviceTypeId,
        Instant startTime,
        String customerFullName,
        String customerEmail,
        String customerPhone,
        String vin,
        String vehicleMake,
        String vehicleModel,
        Integer vehicleYear,
        UUID serviceBayId,
        UUID technicianId,
        String notes) {}
