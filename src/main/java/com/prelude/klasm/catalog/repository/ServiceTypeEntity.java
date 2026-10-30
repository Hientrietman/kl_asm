package com.prelude.klasm.catalog.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "service_type")
public class ServiceTypeEntity {

    @Id
    private UUID id;

    private String code;

    private String name;

    private int durationMinutes;

    private boolean active;

    protected ServiceTypeEntity() {}

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public boolean isActive() {
        return active;
    }
}
