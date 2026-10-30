package com.prelude.klasm.appointment.controller;

import jakarta.validation.constraints.Size;

/** Optional body of {@code POST /api/v1/appointments/{id}/cancel}. */
public record CancelAppointmentRequest(@Size(max = 500) String reason) {}
