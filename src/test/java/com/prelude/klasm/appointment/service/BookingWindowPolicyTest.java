package com.prelude.klasm.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prelude.klasm.common.error.ApiException;
import com.prelude.klasm.common.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BookingWindowPolicyTest {

    private static final Instant NOW = Instant.parse("2030-01-07T08:00:00Z");

    private final BookingWindowPolicy policy = new BookingWindowPolicy(
            Clock.fixed(NOW, ZoneOffset.UTC), new BookingProperties(Duration.ofMinutes(60), Duration.ofDays(90)));

    @Test
    void acceptsStartExactlyAtMinimumLeadTime() {
        assertThatCode(() -> policy.check(NOW.plus(Duration.ofMinutes(60)))).doesNotThrowAnyException();
    }

    @Test
    void acceptsStartExactlyAtMaximumHorizon() {
        assertThatCode(() -> policy.check(NOW.plus(Duration.ofDays(90)))).doesNotThrowAnyException();
    }

    @Test
    void rejectsStartInsideMinimumLeadTime() {
        assertRejected(NOW.plus(Duration.ofMinutes(45)));
    }

    @Test
    void rejectsStartInThePast() {
        assertRejected(NOW.minus(Duration.ofHours(1)));
    }

    @Test
    void rejectsStartBeyondMaximumHorizon() {
        assertRejected(NOW.plus(Duration.ofDays(90)).plus(Duration.ofMinutes(15)));
    }

    private void assertRejected(Instant start) {
        assertThatThrownBy(() -> policy.check(start))
                .isInstanceOfSatisfying(
                        ApiException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_BOOKING_WINDOW));
    }
}
