package com.prelude.klasm.catalog.controller;

import com.prelude.klasm.catalog.service.ServiceTypeView;
import java.util.UUID;

public record ServiceTypeResponse(UUID id, String code, String name, int durationMinutes) {

    static ServiceTypeResponse from(ServiceTypeView view) {
        return new ServiceTypeResponse(view.id(), view.code(), view.name(), view.durationMinutes());
    }
}
