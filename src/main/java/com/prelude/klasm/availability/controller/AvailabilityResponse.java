package com.prelude.klasm.availability.controller;

import com.prelude.klasm.availability.service.AvailabilityView;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Start times in UTC and in the dealership's local offset. Not a reservation (FR-5). */
public record AvailabilityResponse(
        UUID dealershipId, UUID serviceTypeId, LocalDate date, String timeZone, int durationMinutes, List<Slot> slots) {

    public record Slot(OffsetDateTime startTime, OffsetDateTime localStartTime) {}

    static AvailabilityResponse from(AvailabilityView view) {
        List<Slot> slots = view.startTimes().stream()
                .map(start -> new Slot(
                        start.atOffset(ZoneOffset.UTC),
                        start.atZone(view.timeZone()).toOffsetDateTime()))
                .toList();
        return new AvailabilityResponse(
                view.dealershipId(),
                view.serviceTypeId(),
                view.date(),
                view.timeZone().getId(),
                view.durationMinutes(),
                slots);
    }
}
