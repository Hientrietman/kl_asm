package com.prelude.klasm;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A Clock that tests can move, so time rules (lead time, horizon, "already started") are deterministic. */
public class MutableClock extends Clock {

    public static final Instant DEFAULT_NOW = Instant.parse("2030-01-01T00:00:00Z");

    private volatile Instant instant = DEFAULT_NOW;

    public void set(Instant instant) {
        this.instant = instant;
    }

    public void reset() {
        set(DEFAULT_NOW);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
