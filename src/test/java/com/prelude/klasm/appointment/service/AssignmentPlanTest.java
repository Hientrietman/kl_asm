package com.prelude.klasm.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.prelude.klasm.availability.service.BayCandidate;
import com.prelude.klasm.availability.service.TechnicianCandidate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignmentPlanTest {

    private final BayCandidate bay1 = new BayCandidate(UUID.randomUUID(), "BAY-01");
    private final BayCandidate bay2 = new BayCandidate(UUID.randomUUID(), "BAY-02");
    private final TechnicianCandidate tech1 = new TechnicianCandidate(UUID.randomUUID(), "T1", 0);
    private final TechnicianCandidate tech2 = new TechnicianCandidate(UUID.randomUUID(), "T2", 30);

    @Test
    void startsWithFirstBayAndFirstTechnician() {
        AssignmentPlan plan = new AssignmentPlan(List.of(bay1, bay2), List.of(tech1, tech2));

        assertThat(plan.hasCandidate()).isTrue();
        assertThat(plan.bay()).isEqualTo(bay1);
        assertThat(plan.technician()).isEqualTo(tech1);
    }

    @Test
    void bayConflictKeepsTechnician() {
        AssignmentPlan plan = new AssignmentPlan(List.of(bay1, bay2), List.of(tech1, tech2));

        plan.skipBay();

        assertThat(plan.bay()).isEqualTo(bay2);
        assertThat(plan.technician()).isEqualTo(tech1);
    }

    @Test
    void technicianConflictKeepsBay() {
        AssignmentPlan plan = new AssignmentPlan(List.of(bay1, bay2), List.of(tech1, tech2));

        plan.skipTechnician();

        assertThat(plan.bay()).isEqualTo(bay1);
        assertThat(plan.technician()).isEqualTo(tech2);
    }

    @Test
    void isExhaustedWhenEitherListRunsOut() {
        AssignmentPlan plan = new AssignmentPlan(List.of(bay1), List.of(tech1, tech2));

        plan.skipBay();

        assertThat(plan.hasCandidate()).isFalse();
        assertThat(plan.baysExhausted()).isTrue();
    }

    @Test
    void hasNoCandidateWhenAListIsEmpty() {
        assertThat(new AssignmentPlan(List.of(), List.of(tech1)).hasCandidate()).isFalse();
        assertThat(new AssignmentPlan(List.of(bay1), List.of()).hasCandidate()).isFalse();
    }
}
