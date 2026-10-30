package com.prelude.klasm.availability.service;

import java.util.UUID;

/** A free, active service bay of the dealership for the requested range. */
public record BayCandidate(UUID id, String code) {}
