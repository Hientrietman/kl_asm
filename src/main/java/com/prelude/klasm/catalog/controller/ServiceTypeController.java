package com.prelude.klasm.catalog.controller;

import com.prelude.klasm.catalog.service.ServiceTypeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/service-types")
class ServiceTypeController {

    private final ServiceTypeService serviceTypeService;

    ServiceTypeController(ServiceTypeService serviceTypeService) {
        this.serviceTypeService = serviceTypeService;
    }

    @GetMapping
    List<ServiceTypeResponse> list() {
        return serviceTypeService.listActive().stream()
                .map(ServiceTypeResponse::from)
                .toList();
    }
}
