package com.prelude.klasm.appointment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestHashTest {

    private static final UUID DEALERSHIP = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID SERVICE_TYPE = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final Instant START = Instant.parse("2030-01-07T09:00:00Z");

    @Test
    void isStableSha256Hex() {
        String hash = RequestHash.of(command("Jane", null, "key-1"));

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(RequestHash.of(command("Jane", null, "key-1"))).isEqualTo(hash);
    }

    @Test
    void ignoresTheIdempotencyKeyItself() {
        assertThat(RequestHash.of(command("Jane", null, "key-1")))
                .isEqualTo(RequestHash.of(command("Jane", null, "key-2")));
    }

    @Test
    void changesWhenAnyFieldChanges() {
        assertThat(RequestHash.of(command("Jane", null, "k"))).isNotEqualTo(RequestHash.of(command("John", null, "k")));
    }

    @Test
    void distinguishesMissingFromEmptyValues() {
        assertThat(RequestHash.of(command("Jane", null, "k"))).isNotEqualTo(RequestHash.of(command("Jane", "", "k")));
    }

    private static BookingCommand command(String fullName, String notes, String key) {
        return new BookingCommand(
                DEALERSHIP,
                SERVICE_TYPE,
                START,
                fullName,
                "jane@example.com",
                null,
                "1HGCM82633A004352",
                null,
                null,
                null,
                null,
                null,
                notes,
                key);
    }
}
