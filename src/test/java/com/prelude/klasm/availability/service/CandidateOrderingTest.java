package com.prelude.klasm.availability.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidateOrderingTest {

    @Test
    void ordersBaysByCode() {
        List<BayCandidate> ordered = CandidateOrdering.orderBays(List.of(
                new BayCandidate(UUID.randomUUID(), "BAY-03"),
                new BayCandidate(UUID.randomUUID(), "BAY-01"),
                new BayCandidate(UUID.randomUUID(), "BAY-02")));

        assertThat(ordered).extracting(BayCandidate::code).containsExactly("BAY-01", "BAY-02", "BAY-03");
    }

    @Test
    void ordersTechniciansByBookedMinutesThenId() {
        UUID a = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("a0000000-0000-0000-0000-000000000002");
        UUID c = UUID.fromString("a0000000-0000-0000-0000-000000000003");

        List<TechnicianCandidate> ordered = CandidateOrdering.orderTechnicians(List.of(
                new TechnicianCandidate(a, "A", 120),
                new TechnicianCandidate(c, "C", 0),
                new TechnicianCandidate(b, "B", 0)));

        assertThat(ordered).extracting(TechnicianCandidate::id).containsExactly(b, c, a);
    }

    @Test
    void breaksTiesByTextualIdLikePostgres() {
        // Signed UUID.compareTo would put 8xxx... before 1xxx...; PostgreSQL and text order do not
        UUID low = UUID.fromString("10000000-0000-0000-0000-000000000000");
        UUID high = UUID.fromString("80000000-0000-0000-0000-000000000000");

        List<TechnicianCandidate> ordered = CandidateOrdering.orderTechnicians(
                List.of(new TechnicianCandidate(high, "High", 0), new TechnicianCandidate(low, "Low", 0)));

        assertThat(ordered).extracting(TechnicianCandidate::id).containsExactly(low, high);
    }
}
