package com.prelude.klasm.availability.service;

import com.prelude.klasm.dealership.service.DealershipView;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The 15-minute start-time grid of one local day (A-4, FR-5). Pure functions, unit-tested without Spring. */
final class SlotGrid {

    static final int STEP_MINUTES = 15;

    private SlotGrid() {}

    /** Every grid start whose whole service range fits inside the dealership's working hours on {@code date}. */
    static List<Instant> candidateStarts(DealershipView dealership, int durationMinutes, LocalDate date) {
        List<Instant> starts = new ArrayList<>();
        for (DealershipView.WorkingHours hours : dealership.workingHours()) {
            if (hours.dayOfWeek() != date.getDayOfWeek()) {
                continue;
            }
            int open = minuteOfDay(hours.openTime());
            int firstStart = ((open + STEP_MINUTES - 1) / STEP_MINUTES) * STEP_MINUTES;
            int close = minuteOfDay(hours.closeTime());
            for (int minute = firstStart; minute + durationMinutes <= close; minute += STEP_MINUTES) {
                Instant start = ZonedDateTime.of(date, LocalTime.ofSecondOfDay(minute * 60L), dealership.timeZone())
                        .toInstant();
                Instant end = start.plus(Duration.ofMinutes(durationMinutes));
                // Same rules as booking: on the UTC grid and inside one local working day
                if (start.getEpochSecond() % (STEP_MINUTES * 60) == 0 && dealership.isOpenDuring(start, end)) {
                    starts.add(start);
                }
            }
        }
        return starts;
    }

    /** Up to {@code limit} starts closest to {@code target} (excluding it), returned in time order. */
    static List<Instant> nearest(List<Instant> starts, Instant target, int limit) {
        return starts.stream()
                .filter(start -> !start.equals(target))
                .sorted(Comparator.comparing((Instant start) ->
                                Duration.between(start, target).abs())
                        .thenComparing(Comparator.naturalOrder()))
                .limit(limit)
                .sorted()
                .toList();
    }

    private static int minuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }
}
