package com.prelude.klasm.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class VehicleYearValidatorTest {

    private final VehicleYearValidator validator =
            new VehicleYearValidator(Clock.fixed(Instant.parse("2030-06-01T00:00:00Z"), ZoneOffset.UTC));

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {1981, 2000, 2030, 2031})
    void acceptsYearsFrom1981ToNextYear(Integer year) {
        assertThat(validator.isValid(year, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {1980, 2032, 0, -1})
    void rejectsYearsOutsideTheRange(Integer year) {
        assertThat(validator.isValid(year, null)).isFalse();
    }
}
