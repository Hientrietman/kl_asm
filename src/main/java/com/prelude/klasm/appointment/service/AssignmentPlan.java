package com.prelude.klasm.appointment.service;

import com.prelude.klasm.availability.service.BayCandidate;
import com.prelude.klasm.availability.service.TechnicianCandidate;
import java.util.List;

/**
 * Walks the ordered candidates during auto-assignment (FR-2.4, FR-2.8). After an exclusion violation only the
 * resource that conflicted is replaced; the other one is kept because it may still be free.
 */
final class AssignmentPlan {

    private final List<BayCandidate> bays;
    private final List<TechnicianCandidate> technicians;
    private int bayIndex;
    private int technicianIndex;

    AssignmentPlan(List<BayCandidate> bays, List<TechnicianCandidate> technicians) {
        this.bays = List.copyOf(bays);
        this.technicians = List.copyOf(technicians);
    }

    boolean hasCandidate() {
        return bayIndex < bays.size() && technicianIndex < technicians.size();
    }

    BayCandidate bay() {
        return bays.get(bayIndex);
    }

    TechnicianCandidate technician() {
        return technicians.get(technicianIndex);
    }

    void skipBay() {
        bayIndex++;
    }

    void skipTechnician() {
        technicianIndex++;
    }

    boolean baysExhausted() {
        return bayIndex >= bays.size();
    }
}
