package com.prelude.klasm.dealership.repository;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "technician")
public class TechnicianEntity {

    @Id
    private UUID id;

    private UUID dealershipId;

    private String fullName;

    private boolean active;

    // Service types belong to the catalog module, so only their ids are mapped here (ADR-002)
    @ElementCollection
    @CollectionTable(name = "technician_qualification", joinColumns = @JoinColumn(name = "technician_id"))
    @Column(name = "service_type_id")
    private Set<UUID> serviceTypeIds = new HashSet<>();

    protected TechnicianEntity() {}

    public UUID getId() {
        return id;
    }

    public UUID getDealershipId() {
        return dealershipId;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isActive() {
        return active;
    }

    public Set<UUID> getServiceTypeIds() {
        return serviceTypeIds;
    }
}
