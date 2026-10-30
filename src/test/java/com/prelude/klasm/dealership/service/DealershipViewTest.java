package com.prelude.klasm.dealership.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Working-hours rule (FR-1.3, A-3, A-16) evaluated in the dealership's time zone. */
class DealershipViewTest {

    private static final List<DealershipView.WorkingHours> WEEKDAYS_AND_SATURDAY = List.of(
            hours(DayOfWeek.MONDAY, "08:00", "17:00"),
            hours(DayOfWeek.TUESDAY, "08:00", "17:00"),
            hours(DayOfWeek.WEDNESDAY, "08:00", "17:00"),
            hours(DayOfWeek.THURSDAY, "08:00", "17:00"),
            hours(DayOfWeek.FRIDAY, "08:00", "17:00"),
            hours(DayOfWeek.SATURDAY, "08:00", "12:00"));

    private final DealershipView london = dealership("Europe/London");
    private final DealershipView hoChiMinh = dealership("Asia/Ho_Chi_Minh");

    // 2030-01-07 is a Monday in winter (London = UTC+0); 2030-07-08 is a Monday in summer (London = UTC+1)

    @Test
    void acceptsRangeInsideWorkingHours() {
        assertThat(london.isOpenDuring(at("2030-01-07T09:00:00Z"), at("2030-01-07T10:00:00Z")))
                .isTrue();
    }

    @Test
    void acceptsRangeStartingAtOpeningAndEndingAtClosing() {
        assertThat(london.isOpenDuring(at("2030-01-07T08:00:00Z"), at("2030-01-07T17:00:00Z")))
                .isTrue();
    }

    @Test
    void rejectsRangeStartingBeforeOpening() {
        assertThat(london.isOpenDuring(at("2030-01-07T07:45:00Z"), at("2030-01-07T08:45:00Z")))
                .isFalse();
    }

    @Test
    void rejectsRangeEndingAfterClosing() {
        assertThat(london.isOpenDuring(at("2030-01-07T16:30:00Z"), at("2030-01-07T17:30:00Z")))
                .isFalse();
    }

    @Test
    void appliesShorterSaturdayHours() {
        assertThat(london.isOpenDuring(at("2030-01-12T11:00:00Z"), at("2030-01-12T12:00:00Z")))
                .isTrue();
        assertThat(london.isOpenDuring(at("2030-01-12T11:30:00Z"), at("2030-01-12T12:30:00Z")))
                .isFalse();
    }

    @Test
    void rejectsClosedDay() {
        assertThat(london.isOpenDuring(at("2030-01-13T10:00:00Z"), at("2030-01-13T11:00:00Z")))
                .isFalse();
    }

    @Test
    void rejectsRangeSpanningMidnight() {
        DealershipView allDay = new DealershipView(
                UUID.randomUUID(),
                "All day",
                ZoneId.of("UTC"),
                List.of(hours(DayOfWeek.MONDAY, "00:00", "23:59"), hours(DayOfWeek.TUESDAY, "00:00", "23:59")));

        assertThat(allDay.isOpenDuring(at("2030-01-07T23:30:00Z"), at("2030-01-08T00:30:00Z")))
                .isFalse();
    }

    @Test
    void usesDaylightSavingOffsetOfTheDealershipZone() {
        // 07:30Z is 07:30 local in winter (closed) but 08:30 local in summer (open)
        assertThat(london.isOpenDuring(at("2030-01-07T07:30:00Z"), at("2030-01-07T08:30:00Z")))
                .isFalse();
        assertThat(london.isOpenDuring(at("2030-07-08T07:30:00Z"), at("2030-07-08T08:30:00Z")))
                .isTrue();
        // 16:30Z is 17:30 local in summer, after closing
        assertThat(london.isOpenDuring(at("2030-07-08T15:30:00Z"), at("2030-07-08T16:30:00Z")))
                .isFalse();
    }

    @Test
    void evaluatesLocalDayInFarEastZone() {
        // 01:00Z is 08:00 local in Ho Chi Minh City (UTC+7)
        assertThat(hoChiMinh.isOpenDuring(at("2030-01-07T01:00:00Z"), at("2030-01-07T02:00:00Z")))
                .isTrue();
        // Sunday 23:30Z is Monday 06:30 local, before opening
        assertThat(hoChiMinh.isOpenDuring(at("2030-01-06T23:30:00Z"), at("2030-01-07T00:30:00Z")))
                .isFalse();
    }

    private static DealershipView dealership(String zone) {
        return new DealershipView(UUID.randomUUID(), zone, ZoneId.of(zone), WEEKDAYS_AND_SATURDAY);
    }

    private static DealershipView.WorkingHours hours(DayOfWeek day, String open, String close) {
        return new DealershipView.WorkingHours(day, LocalTime.parse(open), LocalTime.parse(close));
    }

    private static Instant at(String value) {
        return Instant.parse(value);
    }
}
