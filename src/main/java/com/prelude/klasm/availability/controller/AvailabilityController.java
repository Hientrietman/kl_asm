package com.prelude.klasm.availability.controller;

import com.prelude.klasm.availability.service.AvailabilityService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dealerships")
class AvailabilityController {

    private final AvailabilityService availabilityService;

    AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    /** {@code date} is a local date in the dealership's time zone. */
    @GetMapping("/{dealershipId}/availability")
    AvailabilityResponse availability(
            @PathVariable UUID dealershipId,
            @RequestParam UUID serviceTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return AvailabilityResponse.from(availabilityService.availability(dealershipId, serviceTypeId, date));
    }
}
