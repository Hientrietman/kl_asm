package com.prelude.klasm.dealership.controller;

import com.prelude.klasm.dealership.service.DealershipService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dealerships")
class DealershipController {

    private final DealershipService dealershipService;

    DealershipController(DealershipService dealershipService) {
        this.dealershipService = dealershipService;
    }

    @GetMapping
    List<DealershipResponse> list() {
        return dealershipService.listActive().stream()
                .map(DealershipResponse::from)
                .toList();
    }

    @GetMapping("/{dealershipId}/service-bays")
    List<ServiceBayResponse> serviceBays(@PathVariable UUID dealershipId) {
        return dealershipService.listServiceBays(dealershipId).stream()
                .map(ServiceBayResponse::from)
                .toList();
    }

    @GetMapping("/{dealershipId}/technicians")
    List<TechnicianResponse> technicians(@PathVariable UUID dealershipId) {
        return dealershipService.listTechnicians(dealershipId).stream()
                .map(TechnicianResponse::from)
                .toList();
    }
}
