package com.prelude.klasm.appointment.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentResourceLockRepository {

    private static final String LOCK_SQL = "SELECT pg_advisory_xact_lock(hashtextextended(:resourceKey, 0))";

    private final NamedParameterJdbcTemplate jdbc;

    public AppointmentResourceLockRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockResources(UUID bayId, UUID technicianId, String vin) {
        List.of("bay:" + bayId, "technician:" + technicianId, "vehicle:" + vin).stream()
                .sorted()
                .forEach(this::lock);
    }

    private void lock(String resourceKey) {
        jdbc.query(LOCK_SQL, new MapSqlParameterSource("resourceKey", resourceKey), resultSet -> {
            while (resultSet.next()) {
                // Consume the result while retaining the transaction-scoped lock.
            }
            return null;
        });
    }
}
