package com.prelude.klasm.availability.service;

import com.prelude.klasm.availability.repository.CandidateQueries;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the availability module. */
@Service
public class AvailabilityService {

    private final CandidateQueries queries;

    public AvailabilityService(CandidateQueries queries) {
        this.queries = queries;
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
}
