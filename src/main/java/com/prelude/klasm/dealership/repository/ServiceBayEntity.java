package com.prelude.klasm.dealership.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "service_bay")
public class ServiceBayEntity {

    @Id
    private UUID id;

    private UUID dealershipId;

    private String code;

    private boolean active;

    protected ServiceBayEntity() {}

    public UUID getId() {
        return id;
    }

    public UUID getDealershipId() {
        return dealershipId;
    }

    public String getCode() {
        return code;
    }

    public boolean isActive() {
        return active;
    }
}
