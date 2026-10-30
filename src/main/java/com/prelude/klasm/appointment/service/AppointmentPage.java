package com.prelude.klasm.appointment.service;

import java.util.List;

public record AppointmentPage(List<AppointmentView> content, int page, int size, long totalElements, int totalPages) {}
