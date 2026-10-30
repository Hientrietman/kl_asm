package com.prelude.klasm.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogMaskingTest {

    @Test
    void keepsOnlyTheLastSixVinCharacters() {
        assertThat(LogMasking.vinSuffix("1HGCM82633A004352")).isEqualTo("004352");
    }

    @Test
    void leavesShortOrMissingValuesUnchanged() {
        assertThat(LogMasking.vinSuffix(null)).isNull();
        assertThat(LogMasking.vinSuffix("ABC")).isEqualTo("ABC");
    }
}
