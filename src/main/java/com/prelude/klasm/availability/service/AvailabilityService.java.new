package com.prelude.klasm.availability.service;

import com.prelude.klasm.availability.repository.CandidateQueries;
import com.prelude.klasm.catalog.service.ServiceTypeService;
import com.prelude.klasm.catalog.service.ServiceTypeView;
import com.prelude.klasm.dealership.service.DealershipService;
import com.prelude.klasm.dealership.service.DealershipView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the availability module. */
@Service
public class AvailabilityService {

    private final CandidateQueries queries;
    private final DealershipService dealershipService;
    private final ServiceTypeService serviceTypeService;
    private final BookingWindowPolicy bookingWindowPolicy;

    public AvailabilityService(
            CandidateQueries queries,
            DealershipService dealershipService,
            ServiceTypeService serviceTypeService,
            BookingWindowPolicy bookingWindowPolicy) {
        this.queries = queries;
        this.dealershipService = dealershipService;
        this.serviceTypeService = serviceTypeService;
        this.bookingWindowPolicy = bookingWindowPolicy;
    }

    /** Free bays for {@code [start, end)}, in assignment order. */
    @Transactional(readOnly = true)
    public List<BayCandidate> freeBays(UUID dealershipId, Instant start, Instant end) {
        return CandidateOrdering.orderBays(queries.freeBays(dealershipId, start, end));
    }

    /** Free qualified technicians for {@code [start, end)}, in assignment order (load on the local day). */
    @Transactional(readOnly = true)
    public List<TechnicianCandidate> freeTechnicians(
            UUID dealershipId, UUID serviceTypeId, Instant start, Instant end, ZoneId zone) {
        LocalDate localDay = start.atZone(zone).toLocalDate();
        Instant dayStart = localDay.atStartOfDay(zone).toInstant();
        Instant dayEnd = localDay.plusDays(1).atStartOfDay(zone).toInstant();
        return CandidateOrdering.orderTechnicians(
                queries.freeQualifiedTechnicians(dealershipId, serviceTypeId, start, end, dayStart, dayEnd));
    }

    /** FR-5. Informational only: a later booking can still return 409. */
    @Transactional(readOnly = true)
    public AvailabilityView availability(UUID dealershipId, UUID serviceTypeId, LocalDate date) {
        DealershipView dealership = dealershipService.getActive(dealershipId);
        ServiceTypeView serviceType = serviceTypeService.getActive(serviceTypeId);
        return new AvailabilityView(
                dealership.id(),
                serviceType.id(),
                date,
                dealership.timeZone(),
                serviceType.durationMinutes(),
                availableStarts(dealership, serviceType.id(), serviceType.durationMinutes(), date));
    }

    /** FR-2.7: up to {@code limit} bookable starts on the same local day, closest to the requested one. */
    @Transactional(readOnly = true)
    public List<Instant> alternativeStarts(
            DealershipView dealership, UUID serviceTypeId, int durationMinutes, Instant requested, int limit) {
        LocalDate localDay = requested.atZone(dealership.timeZone()).toLocalDate();
        return SlotGrid.nearest(
                availableStarts(dealership, serviceTypeId, durationMinutes, localDay), requested, limit);
    }

    private List<Instant> availableStarts(
            DealershipView dealership, UUID serviceTypeId, int durationMinutes, LocalDate date) {
        // Same time rules as booking, so every returned start would pass validation
        List<Instant> candidates = SlotGrid.candidateStarts(dealership, durationMinutes, date).stream()
                .filter(bookingWindowPolicy::allows)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<Instant> free = new HashSet<>(queries.freeStarts(
                dealership.id(),
                serviceTypeId,
                durationMinutes,
                candidates.get(0),
                candidates.get(candidates.size() - 1)));
        return candidates.stream().filter(free::contains).toList();
    }
}
