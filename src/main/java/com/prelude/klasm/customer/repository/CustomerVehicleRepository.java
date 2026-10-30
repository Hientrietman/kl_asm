package com.prelude.klasm.customer.repository;

import java.sql.Types;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Find-or-create for customers and vehicles. {@code ON CONFLICT DO NOTHING} never updates an existing row (A-13) and
 * waits for a concurrent insert of the same key, so the following SELECT always finds exactly one row.
 */
@Repository
public class CustomerVehicleRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CustomerVehicleRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID findOrCreateCustomer(String fullName, String email, String phone) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fullName", fullName, Types.VARCHAR)
                .addValue("email", email, Types.VARCHAR)
                .addValue("phone", phone, Types.VARCHAR);
        jdbc.update("""
                INSERT INTO customer (full_name, email, phone) VALUES (:fullName, :email, :phone)
                ON CONFLICT ((lower(email))) DO NOTHING
                """, params);
        return jdbc.queryForObject("SELECT id FROM customer WHERE lower(email) = lower(:email)", params, UUID.class);
    }

    public UUID findOrCreateVehicle(String vin, String make, String model, Integer year) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vin", vin, Types.VARCHAR)
                .addValue("make", make, Types.VARCHAR)
                .addValue("model", model, Types.VARCHAR)
                .addValue("year", year, Types.SMALLINT);
        jdbc.update("""
                INSERT INTO vehicle (vin, make, model, year) VALUES (:vin, :make, :model, :year)
                ON CONFLICT (vin) DO NOTHING
                """, params);
        return jdbc.queryForObject("SELECT id FROM vehicle WHERE vin = :vin", params, UUID.class);
    }
}
