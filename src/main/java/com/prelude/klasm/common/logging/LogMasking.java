package com.prelude.klasm.common.logging;

/** Helpers that keep personal data out of logs (AGENTS.md §7, NFR-3). */
public final class LogMasking {

    private static final int VIN_SUFFIX_LENGTH = 6;

    private LogMasking() {}

    /** Only the last 6 characters of a VIN may be logged. */
    public static String vinSuffix(String vin) {
        if (vin == null || vin.length() <= VIN_SUFFIX_LENGTH) {
            return vin;
        }
        return vin.substring(vin.length() - VIN_SUFFIX_LENGTH);
    }
}
