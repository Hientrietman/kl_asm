package com.prelude.klasm.availability.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.prelude.klasm.dealership.service.DealershipView;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SlotGridTest {

    private static final LocalDate MONDAY = LocalDate.of(2030, 1, 7);

    @Test
    void generatesQuarterHourStartsThatFitBeforeClosing() {
        DealershipView london = dealership("Europe/London", "08:00", "10:00");

        List<Instant> starts = SlotGrid.candidateStarts(london, 60, MONDAY);

        assertThat(starts)
                .containsExactly(
                        Instant.parse("2030-01-07T08:00:00Z"),
                        Instant.parse("2030-01-07T08:15:00Z"),
                        Instant.parse("2030-01-07T08:30:00Z"),
                        Instant.parse("2030-01-07T08:45:00Z"),
                        Instant.parse("2030-01-07T09:00:00Z"));
    }

    @Test
    void usesTheDealershipZone() {
        DealershipView hoChiMinh = dealership("Asia/Ho_Chi_Minh", "08:00", "09:00");

        assertThat(SlotGrid.candidateStarts(hoChiMinh, 60, MONDAY))
                .containsExactly(Instant.parse("2030-01-07T01:00:00Z"));
    }

    @Test
    void roundsAnOffGridOpeningUpToTheNextQuarter() {
        DealershipView london = dealership("Europe/London", "08:10", "09:30");

        assertThat(SlotGrid.candidateStarts(london, 60, MONDAY))
                .containsExactly(Instant.parse("2030-01-07T08:15:00Z"), Instant.parse("2030-01-07T08:30:00Z"));
    }

    @Test
    void returnsNothingOnClosedDayOrWhenServiceIsLongerThanTheDay() {
        DealershipView london = dealership("Europe/London", "08:00", "10:00");

        assertThat(SlotGrid.candidateStarts(london, 60, MONDAY.minusDays(1))).isEmpty();
        assertThat(SlotGrid.candidateStarts(london, 180, MONDAY)).isEmpty();
    }

    @Test
    void nearestPicksClosestStartsInTimeOrder() {
        List<Instant> starts = List.of(
                Instant.parse("2030-01-07T08:00:00Z"),
                Instant.parse("2030-01-07T09:45:00Z"),
                Instant.parse("2030-01-07T10:00:00Z"),
                Instant.parse("2030-01-07T10:30:00Z"),
                Instant.parse("2030-01-07T15:00:00Z"));

        assertThat(SlotGrid.nearest(starts, Instant.parse("2030-01-07T10:00:00Z"), 3))
                .containsExactly(
                        Instant.parse("2030-01-07T08:00:00Z"),
                        Instant.parse("2030-01-07T09:45:00Z"),
                        Instant.parse("2030-01-07T10:30:00Z"));
    }

    private static DealershipView dealership(String zone, String open, String close) {
        return new DealershipView(
                UUID.randomUUID(),
                zone,
                ZoneId.of(zone),
                List.of(new DealershipView.WorkingHours(
                        DayOfWeek.MONDAY, LocalTime.parse(open), LocalTime.parse(close))));
    }
}
