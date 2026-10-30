package com.prelude.klasm.catalog.service;

import java.util.UUID;

public record ServiceTypeView(UUID id, String code, String name, int durationMinutes) {}
