package com.prelude.klasm.dealership.controller;

import com.prelude.klasm.dealership.service.ServiceBayView;
import java.util.UUID;

public record ServiceBayResponse(UUID id, String code) {

    static ServiceBayResponse from(ServiceBayView view) {
        return new ServiceBayResponse(view.id(), view.code());
    }
}
