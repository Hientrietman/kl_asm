package com.prelude.klasm.appointment.service;

import java.time.Instant;
import java.util.UUID;

/** FR-4.2 list filters; every field except paging is optional. {@code from} is inclusive, {@code to} exclusive. */
public record AppointmentFilter(
        UUID dealershipId, Instant from, Instant to, AppointmentStatus status, String vin, int page, int size) {}
