package com.prelude.klasm.dealership.controller;

import com.prelude.klasm.dealership.service.TechnicianView;
import java.util.List;
import java.util.UUID;

public record TechnicianResponse(UUID id, String fullName, List<UUID> serviceTypeIds) {

    static TechnicianResponse from(TechnicianView view) {
        return new TechnicianResponse(view.id(), view.fullName(), view.serviceTypeIds());
    }
}
