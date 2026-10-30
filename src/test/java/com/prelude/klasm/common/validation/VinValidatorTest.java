package com.prelude.klasm.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class VinValidatorTest {

    private final VinValidator validator = new VinValidator();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"1HGCM82633A004352", "WVWZZZ1JZXW000001", "JH4KA7561PC008269"})
    void acceptsValidVinsAndNull(String vin) {
        assertThat(validator.isValid(vin, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "1HGCM82633A00435", // 16 characters
                "1HGCM82633A0043521", // 18 characters
                "1HGCM82633A00435I", // I is not allowed
                "1HGCM82633A00435O", // O is not allowed
                "1HGCM82633A00435Q", // Q is not allowed
                "1hgcm82633a004352", // lower case is normalized by the request, not accepted here
                "1HGCM82633A00435-",
                "1HGCM82633A 04352"
            })
    void rejectsInvalidVins(String vin) {
        assertThat(validator.isValid(vin, null)).isFalse();
    }
}
