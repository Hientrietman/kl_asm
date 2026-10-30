package com.prelude.klasm.appointment.service;

import com.prelude.klasm.appointment.repository.AppointmentEntity;
import com.prelude.klasm.appointment.repository.AppointmentRepository;
import com.prelude.klasm.dealership.service.DealershipService;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads appointments and turns them into views, resolving bay codes, names and zones in batches. */
@Service
public class AppointmentQueryService {

    private final AppointmentRepository appointments;
    private final DealershipService dealershipService;

    public AppointmentQueryService(AppointmentRepository appointments, DealershipService dealershipService) {
        this.appointments = appointments;
        this.dealershipService = dealershipService;
    }

    /** The stored appointment for an idempotency key, with the request hash it was created from. */
    @Transactional(readOnly = true)
    public Optional<StoredBooking> findByIdempotencyKey(String idempotencyKey) {
        return appointments
                .findByIdempotencyKey(idempotencyKey)
                .map(entity -> new StoredBooking(toViews(List.of(entity)).get(0), entity.getRequestHash()));
    }

    List<AppointmentView> toViews(List<AppointmentEntity> entities) {
        Map<UUID, ZoneId> zones = dealershipService.timeZones(ids(entities, AppointmentEntity::getDealershipId));
        Map<UUID, String> bayCodes =
                dealershipService.serviceBayCodes(ids(entities, AppointmentEntity::getServiceBayId));
        Map<UUID, String> technicianNames =
                dealershipService.technicianNames(ids(entities, AppointmentEntity::getTechnicianId));
        return entities.stream()
                .map(entity -> new AppointmentView(
                        entity.getId(),
                        entity.getStatus(),
                        entity.getDealershipId(),
                        zones.get(entity.getDealershipId()),
                        entity.getServiceTypeId(),
                        new AppointmentView.ServiceBay(
                                entity.getServiceBayId(), bayCodes.get(entity.getServiceBayId())),
                        new AppointmentView.Technician(
                                entity.getTechnicianId(), technicianNames.get(entity.getTechnicianId())),
                        entity.getCustomerId(),
                        entity.getVehicleId(),
                        entity.getStartTime(),
                        entity.getEndTime(),
                        entity.getCancelledAt(),
                        entity.getCancellationReason()))
                .toList();
    }

    private static Set<UUID> ids(List<AppointmentEntity> entities, Function<AppointmentEntity, UUID> id) {
        return entities.stream().map(id).collect(Collectors.toSet());
    }

    public record StoredBooking(AppointmentView appointment, String requestHash) {}
}
