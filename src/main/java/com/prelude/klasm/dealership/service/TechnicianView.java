package com.prelude.klasm.dealership.service;

import java.util.List;
import java.util.UUID;

public record TechnicianView(UUID id, String fullName, List<UUID> serviceTypeIds) {}
