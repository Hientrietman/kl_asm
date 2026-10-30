package com.prelude.klasm.appointment.service;

import com.prelude.klasm.common.error.ApiException;
import com.prelude.klasm.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** FR-1.3: the start must be at least the minimum lead time and at most the maximum horizon from now. */
@Component
public class BookingWindowPolicy {

    private final Clock clock;
    private final BookingProperties properties;

    public BookingWindowPolicy(Clock clock, BookingProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public void check(Instant startTime) {
        Instant now = clock.instant();
        Instant earliest = now.plus(properties.minLeadTime());
        Instant latest = now.plus(properties.maxHorizon());
        if (startTime.isBefore(earliest) || startTime.isAfter(latest)) {
            throw new ApiException(
                    ErrorCode.INVALID_BOOKING_WINDOW,
                    "startTime must be between " + properties.minLeadTime().toMinutes() + " minutes and "
                            + properties.maxHorizon().toDays() + " days from now.");
        }
    }
}
