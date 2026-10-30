package com.prelude.klasm.appointment.repository;

/** A database constraint rejected the appointment insert. */
public class AppointmentConflictException extends RuntimeException {

    public enum Kind {
        SERVICE_BAY,
        TECHNICIAN,
        VEHICLE,
        IDEMPOTENCY_KEY
    }

    private final Kind kind;

    public AppointmentConflictException(Kind kind, Throwable cause) {
        super("Appointment insert rejected: " + kind, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
