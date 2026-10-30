package com.prelude.klasm.dealership.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public record DealershipView(UUID id, String name, ZoneId timeZone, List<WorkingHours> workingHours) {

    /** Opening interval on one local weekday. A day without an entry is closed. */
    public record WorkingHours(DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime) {}
}
