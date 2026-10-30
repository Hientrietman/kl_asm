package com.prelude.klasm.dealership.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record DealershipView(UUID id, String name, ZoneId timeZone, List<WorkingHours> workingHours) {

    /** Opening interval on one local weekday. A day without an entry is closed. */
    public record WorkingHours(DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime) {}

    /**
     * FR-1.3: the half-open range {@code [start, end)} lies inside the working hours of one local day in the
     * dealership's time zone (A-3, A-16). Ending exactly at closing time is allowed.
     */
    public boolean isOpenDuring(Instant start, Instant end) {
        ZonedDateTime localStart = start.atZone(timeZone);
        ZonedDateTime localEnd = end.atZone(timeZone);
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
            return false;
        }
        return workingHours.stream()
                .filter(hours -> hours.dayOfWeek() == localStart.getDayOfWeek())
                .anyMatch(hours -> !localStart.toLocalTime().isBefore(hours.openTime())
                        && !localEnd.toLocalTime().isAfter(hours.closeTime()));
    }
}
