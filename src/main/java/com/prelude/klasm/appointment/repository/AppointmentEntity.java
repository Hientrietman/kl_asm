package com.prelude.klasm.appointment.repository;

import com.prelude.klasm.appointment.service.AppointmentStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Read and update model of an appointment. New rows are inserted by {@link AppointmentInsertRepository}. */
@Entity
@Table(name = "appointment")
public class AppointmentEntity {

    @Id
    private UUID id;

    private UUID dealershipId;
    private UUID customerId;
    private UUID vehicleId;
    private UUID technicianId;
    private UUID serviceBayId;
    private UUID serviceTypeId;
    private Instant startTime;
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    private AppointmentStatus status;

    private Instant cancelledAt;
    private String cancellationReason;
    private String idempotencyKey;
    private String requestHash;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;

    protected AppointmentEntity() {}

    public void cancel(String reason, Instant now) {
        status = AppointmentStatus.CANCELLED;
        cancellationReason = reason;
        cancelledAt = now;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDealershipId() {
        return dealershipId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getTechnicianId() {
        return technicianId;
    }

    public UUID getServiceBayId() {
        return serviceBayId;
    }

    public UUID getServiceTypeId() {
        return serviceTypeId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
