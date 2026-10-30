package com.prelude.klasm.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuarterHourGridValidatorTest {

    private final QuarterHourGridValidator validator = new QuarterHourGridValidator();

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2030-01-07T09:00:00Z",
                "2030-01-07T09:15:00Z",
                "2030-01-07T09:30:00+07:00",
                "2030-01-07T09:45:00-05:00",
                "2030-01-07T09:15:00+05:45"
            })
    void acceptsStartsOnTheGrid(String value) {
        assertThat(validator.isValid(OffsetDateTime.parse(value), null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2030-01-07T09:10:00Z",
                "2030-01-07T09:15:30Z",
                "2030-01-07T09:15:00.001Z",
                "2030-01-07T09:15:00+05:30:30"
            })
    void rejectsStartsOffTheGrid(String value) {
        assertThat(validator.isValid(OffsetDateTime.parse(value), null)).isFalse();
    }

    @Test
    void acceptsNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
