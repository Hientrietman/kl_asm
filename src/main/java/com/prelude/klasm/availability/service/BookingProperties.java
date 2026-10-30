package com.prelude.klasm.availability.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Booking window (A-15): {@code booking.min-lead-time} and {@code booking.max-horizon}. */
@ConfigurationProperties("booking")
public record BookingProperties(Duration minLeadTime, Duration maxHorizon) {}
