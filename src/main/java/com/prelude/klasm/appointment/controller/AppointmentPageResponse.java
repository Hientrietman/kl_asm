package com.prelude.klasm.appointment.controller;

import com.prelude.klasm.appointment.service.AppointmentPage;
import java.util.List;

public record AppointmentPageResponse(
        List<AppointmentResponse> content, int page, int size, long totalElements, int totalPages) {

    static AppointmentPageResponse from(AppointmentPage page) {
        return new AppointmentPageResponse(
                page.content().stream().map(AppointmentResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
