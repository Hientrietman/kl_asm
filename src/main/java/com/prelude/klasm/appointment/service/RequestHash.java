package com.prelude.klasm.appointment.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SHA-256 of the normalized booking request (ADR-010). Built from the command, not the raw body, so whitespace,
 * field order and letter case that normalization removes do not make a retry look like a different request.
 */
final class RequestHash {

    private static final String SEPARATOR = "\u001f";
    private static final String NULL = "\u0000";

    private RequestHash() {}

    static String of(BookingCommand command) {
        String canonical = Arrays.stream(new Object[] {
                    command.dealershipId(),
                    command.serviceTypeId(),
                    command.startTime(),
                    command.customerFullName(),
                    command.customerEmail(),
                    command.customerPhone(),
                    command.vin(),
                    command.vehicleMake(),
                    command.vehicleModel(),
                    command.vehicleYear(),
                    command.serviceBayId(),
                    command.technicianId(),
                    command.notes()
                })
                .map(value -> Objects.toString(value, NULL))
                .collect(Collectors.joining(SEPARATOR));
        return HexFormat.of().formatHex(sha256(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            // Every Java platform is required to provide SHA-256
            throw new IllegalStateException(ex);
        }
    }
}
