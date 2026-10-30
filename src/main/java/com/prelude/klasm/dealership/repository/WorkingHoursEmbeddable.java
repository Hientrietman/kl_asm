package com.prelude.klasm.dealership.repository;

import jakarta.persistence.Embeddable;
import java.time.LocalTime;

@Embeddable
public class WorkingHoursEmbeddable {

    /** ISO day of week, 1 = Monday ... 7 = Sunday. Column is smallint. */
    private short dayOfWeek;

    private LocalTime openTime;

    private LocalTime closeTime;

    protected WorkingHoursEmbeddable() {}

    public short getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpenTime() {
        return openTime;
    }

    public LocalTime getCloseTime() {
        return closeTime;
    }
}
