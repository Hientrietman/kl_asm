package com.prelude.klasm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Verifies the ADR-003 exclusion constraints directly against PostgreSQL, bypassing the application. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaConstraintsIT {

    private static final OffsetDateTime TEN = OffsetDateTime.of(2030, 1, 7, 10, 0, 0, 0, ZoneOffset.UTC);

    private final JdbcTemplate jdbc;

    private UUID dealership;
    private UUID bay1;
    private UUID bay2;
    private UUID tech1;
    private UUID tech2;
    private UUID serviceType;
    private UUID customer;
    private UUID vehicle1;
    private UUID vehicle2;

    @Autowired
    SchemaConstraintsIT(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @BeforeEach
    void insertReferenceData() {
        dealership = insert("INSERT INTO dealership (name, time_zone) VALUES ('Test', 'Europe/London') RETURNING id");
        bay1 = insert("INSERT INTO service_bay (dealership_id, code) VALUES (?, 'B1') RETURNING id", dealership);
        bay2 = insert("INSERT INTO service_bay (dealership_id, code) VALUES (?, 'B2') RETURNING id", dealership);
        tech1 = insert("INSERT INTO technician (dealership_id, full_name) VALUES (?, 'T1') RETURNING id", dealership);
        tech2 = insert("INSERT INTO technician (dealership_id, full_name) VALUES (?, 'T2') RETURNING id", dealership);
        serviceType = insert(
                "INSERT INTO service_type (code, name, duration_minutes) VALUES ('OIL', 'Oil change', 60) RETURNING id");
        customer = insert(
                "INSERT INTO customer (full_name, email) VALUES ('Customer', 'customer@example.com') RETURNING id");
        vehicle1 = insert("INSERT INTO vehicle (vin) VALUES ('1HGCM82633A004352') RETURNING id");
        vehicle2 = insert("INSERT INTO vehicle (vin) VALUES ('2HGCM82633A004353') RETURNING id");
    }

    @Test
    void overlappingConfirmedAppointmentsOnSameBayAreRejected() {
        insertAppointment(bay1, tech1, vehicle1, TEN, TEN.plusHours(1), "CONFIRMED");

        assertExclusionViolation(
                () -> insertAppointment(bay1, tech2, vehicle2, TEN.plusMinutes(30), TEN.plusMinutes(90), "CONFIRMED"),
                "ex_appointment_bay_no_overlap");
    }

    @Test
    void overlappingConfirmedAppointmentsForSameTechnicianAreRejected() {
        insertAppointment(bay1, tech1, vehicle1, TEN, TEN.plusHours(1), "CONFIRMED");

        assertExclusionViolation(
                () -> insertAppointment(bay2, tech1, vehicle2, TEN.plusMinutes(30), TEN.plusMinutes(90), "CONFIRMED"),
                "ex_appointment_technician_no_overlap");
    }

    @Test
    void overlappingConfirmedAppointmentsForSameVehicleAreRejected() {
        insertAppointment(bay1, tech1, vehicle1, TEN, TEN.plusHours(1), "CONFIRMED");

        assertExclusionViolation(
                () -> insertAppointment(bay2, tech2, vehicle1, TEN.plusMinutes(30), TEN.plusMinutes(90), "CONFIRMED"),
                "ex_appointment_vehicle_no_overlap");
    }

    @Test
    void adjacentAppointmentsDoNotOverlapBecauseRangesAreHalfOpen() {
        insertAppointment(bay1, tech1, vehicle1, TEN, TEN.plusHours(1), "CONFIRMED");

        assertThatCode(() -> insertAppointment(bay1, tech1, vehicle1, TEN.plusHours(1), TEN.plusHours(2), "CONFIRMED"))
                .doesNotThrowAnyException();
    }

    @Test
    void cancelledAppointmentDoesNotBlockResources() {
        insertAppointment(bay1, tech1, vehicle1, TEN, TEN.plusHours(1), "CANCELLED");

        assertThatCode(() -> insertAppointment(bay1, tech1, vehicle1, TEN, TEN.plusHours(1), "CONFIRMED"))
                .doesNotThrowAnyException();
    }

    private void assertExclusionViolation(Runnable insert, String constraintName) {
        assertThatThrownBy(insert::run).isInstanceOf(DataAccessException.class).satisfies(ex -> {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo("23P01");
            assertThat(cause.getMessage()).contains(constraintName);
        });
    }

    private void insertAppointment(
            UUID bay, UUID tech, UUID vehicle, OffsetDateTime start, OffsetDateTime end, String status) {
        jdbc.update("""
                INSERT INTO appointment (dealership_id, customer_id, vehicle_id, technician_id, service_bay_id,
                    service_type_id, start_time, end_time, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, dealership, customer, vehicle, tech, bay, serviceType, start, end, status, TEN, TEN);
    }

    private UUID insert(String sql, Object... args) {
        return jdbc.queryForObject(sql, UUID.class, args);
    }
}
