package com.prelude.klasm.availability.service;

import java.util.Comparator;
import java.util.List;

/** Deterministic assignment order (FR-2.4, ADR-009). */
public final class CandidateOrdering {

    /** Lowest bay code first. */
    static final Comparator<BayCandidate> BAYS = Comparator.comparing(BayCandidate::code);

    /** Fewest booked minutes on the local day first; ties broken by id (text order, same as PostgreSQL). */
    static final Comparator<TechnicianCandidate> TECHNICIANS = Comparator.comparingLong(
                    TechnicianCandidate::bookedMinutes)
            .thenComparing(candidate -> candidate.id().toString());

    private CandidateOrdering() {}

    public static List<BayCandidate> orderBays(List<BayCandidate> bays) {
        return bays.stream().sorted(BAYS).toList();
    }

    public static List<TechnicianCandidate> orderTechnicians(List<TechnicianCandidate> technicians) {
        return technicians.stream().sorted(TECHNICIANS).toList();
    }
}
