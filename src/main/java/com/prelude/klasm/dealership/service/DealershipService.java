package com.prelude.klasm.dealership.service;

import com.prelude.klasm.common.error.ApiException;
import com.prelude.klasm.common.error.ErrorCode;
import com.prelude.klasm.dealership.repository.DealershipEntity;
import com.prelude.klasm.dealership.repository.DealershipRepository;
import com.prelude.klasm.dealership.repository.ServiceBayRepository;
import com.prelude.klasm.dealership.repository.TechnicianEntity;
import com.prelude.klasm.dealership.repository.TechnicianRepository;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the dealership module. */
@Service
public class DealershipService {

    private final DealershipRepository dealerships;
    private final ServiceBayRepository serviceBays;
    private final TechnicianRepository technicians;

    public DealershipService(
            DealershipRepository dealerships, ServiceBayRepository serviceBays, TechnicianRepository technicians) {
        this.dealerships = dealerships;
        this.serviceBays = serviceBays;
        this.technicians = technicians;
    }

    @Transactional(readOnly = true)
    public List<DealershipView> listActive() {
        return dealerships.findActiveWithWorkingHours().stream()
                .map(DealershipService::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceBayView> listServiceBays(UUID dealershipId) {
        requireActive(dealershipId);
        return serviceBays.findByDealershipIdAndActiveTrueOrderByCode(dealershipId).stream()
                .map(bay -> new ServiceBayView(bay.getId(), bay.getCode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TechnicianView> listTechnicians(UUID dealershipId) {
        requireActive(dealershipId);
        return technicians.findActiveWithQualifications(dealershipId).stream()
                .map(DealershipService::toView)
                .toList();
    }

    private void requireActive(UUID dealershipId) {
        if (!dealerships.existsByIdAndActiveTrue(dealershipId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Dealership " + dealershipId + " not found.");
        }
    }

    private static DealershipView toView(DealershipEntity entity) {
        List<DealershipView.WorkingHours> hours = entity.getWorkingHours().stream()
                .map(h -> new DealershipView.WorkingHours(
                        DayOfWeek.of(h.getDayOfWeek()), h.getOpenTime(), h.getCloseTime()))
                .sorted(Comparator.comparing(DealershipView.WorkingHours::dayOfWeek))
                .toList();
        return new DealershipView(entity.getId(), entity.getName(), ZoneId.of(entity.getTimeZone()), hours);
    }

    private static TechnicianView toView(TechnicianEntity entity) {
        List<UUID> serviceTypeIds = entity.getServiceTypeIds().stream().sorted().toList();
        return new TechnicianView(entity.getId(), entity.getFullName(), serviceTypeIds);
    }
}
