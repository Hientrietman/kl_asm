package com.prelude.klasm.dealership.controller;

import com.prelude.klasm.dealership.service.DealershipView;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record DealershipResponse(UUID id, String name, String timeZone, List<WorkingHours> workingHours) {

    public record WorkingHours(DayOfWeek dayOfWeek, LocalTime openTime, LocalTime closeTime) {}

    static DealershipResponse from(DealershipView view) {
        List<WorkingHours> hours = view.workingHours().stream()
                .map(h -> new WorkingHours(h.dayOfWeek(), h.openTime(), h.closeTime()))
                .toList();
        return new DealershipResponse(view.id(), view.name(), view.timeZone().getId(), hours);
    }
}
