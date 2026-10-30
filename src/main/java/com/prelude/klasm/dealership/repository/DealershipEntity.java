package com.prelude.klasm.dealership.repository;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "dealership")
public class DealershipEntity {

    @Id
    private UUID id;

    private String name;

    private String timeZone;

    private boolean active;

    @ElementCollection
    @CollectionTable(name = "dealership_working_hours", joinColumns = @JoinColumn(name = "dealership_id"))
    private Set<WorkingHoursEmbeddable> workingHours = new HashSet<>();

    protected DealershipEntity() {}

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public boolean isActive() {
        return active;
    }

    public Set<WorkingHoursEmbeddable> getWorkingHours() {
        return workingHours;
    }
}
