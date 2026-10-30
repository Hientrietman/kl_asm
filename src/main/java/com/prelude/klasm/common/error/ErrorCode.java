package com.prelude.klasm.common.error;

import org.springframework.http.HttpStatus;

/** Stable error codes from the error catalog in docs/requirements/requirements.md. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT),
    NO_AVAILABILITY(HttpStatus.CONFLICT),
    VEHICLE_ALREADY_BOOKED(HttpStatus.CONFLICT),
    OUTSIDE_WORKING_HOURS(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_BOOKING_WINDOW(HttpStatus.UNPROCESSABLE_ENTITY),
    TECHNICIAN_NOT_QUALIFIED(HttpStatus.UNPROCESSABLE_ENTITY),
    RESOURCE_DEALERSHIP_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY),
    IDEMPOTENCY_KEY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY),
    APPOINTMENT_NOT_CANCELLABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
