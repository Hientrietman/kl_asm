package com.prelude.klasm.availability.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** FR-5: start times on one local date at which a bay and a qualified technician are both free. */
public record AvailabilityView(
        UUID dealershipId,
        UUID serviceTypeId,
        LocalDate date,
        ZoneId timeZone,
        int durationMinutes,
        List<Instant> startTimes) {}
