package com.prelude.klasm.availability.service;

import java.util.UUID;

/** A free, active, qualified technician with the minutes already booked on the local day. */
public record TechnicianCandidate(UUID id, String fullName, long bookedMinutes) {}
