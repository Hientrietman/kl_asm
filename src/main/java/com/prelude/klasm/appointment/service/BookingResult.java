package com.prelude.klasm.appointment.service;

/** Outcome of a booking: {@code created} is false when an idempotent retry returned the original appointment. */
public record BookingResult(AppointmentView appointment, boolean created) {}
