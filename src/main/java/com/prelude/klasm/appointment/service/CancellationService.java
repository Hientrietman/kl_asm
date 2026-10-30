package com.prelude.klasm.appointment.service;

import com.prelude.klasm.appointment.repository.AppointmentEntity;
import com.prelude.klasm.appointment.repository.AppointmentRepository;
import com.prelude.klasm.common.error.ApiException;
import com.prelude.klasm.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-4.3: soft cancel (A-9). Cancelled rows stop blocking resources because the constraints cover CONFIRMED only. */
@Service
public class CancellationService {

    private static final Logger log = LoggerFactory.getLogger(CancellationService.class);

    private final AppointmentRepository appointments;
    private final AppointmentQueryService queryService;
    private final Clock clock;

    public CancellationService(AppointmentRepository appointments, AppointmentQueryService queryService, Clock clock) {
        this.appointments = appointments;
        this.queryService = queryService;
        this.clock = clock;
    }

    @Transactional
    public AppointmentView cancel(UUID id, String reason) {
        AppointmentEntity appointment =
                appointments.findById(id).orElseThrow(() -> AppointmentQueryService.notFound(id));
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return queryService.toViews(List.of(appointment)).get(0);
        }
        Instant now = clock.instant();
        if (!appointment.getStartTime().isAfter(now)) {
            throw new ApiException(
                    ErrorCode.APPOINTMENT_NOT_CANCELLABLE, "Appointment " + id + " has already started or finished.");
        }
        appointment.cancel(reason, now);
        // The reason is free text and may contain personal data, so it is not logged
        log.info("Appointment cancelled appointmentId={} dealershipId={}", id, appointment.getDealershipId());
        return queryService.toViews(List.of(appointment)).get(0);
    }
}
