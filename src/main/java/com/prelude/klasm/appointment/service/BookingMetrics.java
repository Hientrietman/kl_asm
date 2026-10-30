package com.prelude.klasm.appointment.service;

import com.prelude.klasm.common.error.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Business metrics from design document section 8.2. Registered eagerly so they are scraped as 0 from startup. */
@Component
public class BookingMetrics {

    private final Counter booked;
    private final Counter retries;
    private final Map<ErrorCode, Counter> conflicts;
    private final Timer duration;

    public BookingMetrics(MeterRegistry registry) {
        booked = Counter.builder("appointments.booked")
                .description("Appointments created")
                .register(registry);
        retries = Counter.builder("booking.assignment.retries")
                .description("Insert attempts repeated with another candidate after an exclusion violation")
                .register(registry);
        conflicts = Map.of(
                ErrorCode.RESOURCE_CONFLICT, conflictCounter(registry, "resource"),
                ErrorCode.NO_AVAILABILITY, conflictCounter(registry, "no_availability"),
                ErrorCode.VEHICLE_ALREADY_BOOKED, conflictCounter(registry, "vehicle"));
        duration = Timer.builder("booking.duration")
                .description("End-to-end booking latency")
                .register(registry);
    }

    void booked() {
        booked.increment();
    }

    void retried() {
        retries.increment();
    }

    void rejected(ErrorCode code) {
        Counter counter = conflicts.get(code);
        if (counter != null) {
            counter.increment();
        }
    }

    Timer duration() {
        return duration;
    }

    private static Counter conflictCounter(MeterRegistry registry, String reason) {
        return Counter.builder("booking.conflicts")
                .description("Booking requests rejected with 409, by reason")
                .tag("reason", reason)
                .register(registry);
    }
}
