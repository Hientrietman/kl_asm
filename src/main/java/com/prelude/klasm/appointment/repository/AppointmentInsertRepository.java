package com.prelude.klasm.appointment.repository;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Inserts a confirmed appointment with plain SQL so that a constraint violation surfaces as the original
 * {@link PSQLException}, whose constraint name tells which resource conflicted (ADR-003).
 */
@Repository
public class AppointmentInsertRepository {

    static final String EXCLUSION_VIOLATION = "23P01";
    static final String UNIQUE_VIOLATION = "23505";

    private static final Map<String, AppointmentConflictException.Kind> CONSTRAINTS = Map.of(
            "ex_appointment_bay_no_overlap", AppointmentConflictException.Kind.SERVICE_BAY,
            "ex_appointment_technician_no_overlap", AppointmentConflictException.Kind.TECHNICIAN,
            "ex_appointment_vehicle_no_overlap", AppointmentConflictException.Kind.VEHICLE,
            "uq_appointment_idempotency_key", AppointmentConflictException.Kind.IDEMPOTENCY_KEY);

    private final NamedParameterJdbcTemplate jdbc;

    public AppointmentInsertRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @throws AppointmentConflictException when an exclusion or idempotency-key constraint rejects the row */
    public void insertConfirmed(NewAppointment appointment) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", appointment.id())
                .addValue("dealershipId", appointment.dealershipId())
                .addValue("customerId", appointment.customerId())
                .addValue("vehicleId", appointment.vehicleId())
                .addValue("technicianId", appointment.technicianId())
                .addValue("serviceBayId", appointment.serviceBayId())
                .addValue("serviceTypeId", appointment.serviceTypeId())
                .addValue("startTime", utc(appointment.startTime()))
                .addValue("endTime", utc(appointment.endTime()))
                .addValue("idempotencyKey", appointment.idempotencyKey(), Types.VARCHAR)
                .addValue("requestHash", appointment.requestHash(), Types.VARCHAR)
                .addValue("notes", appointment.notes(), Types.VARCHAR)
                .addValue("createdAt", utc(appointment.createdAt()));
        try {
            jdbc.update("""
                    INSERT INTO appointment (id, dealership_id, customer_id, vehicle_id, technician_id, service_bay_id,
                        service_type_id, start_time, end_time, status, idempotency_key, request_hash, notes,
                        created_at, updated_at)
                    VALUES (:id, :dealershipId, :customerId, :vehicleId, :technicianId, :serviceBayId,
                        :serviceTypeId, :startTime, :endTime, 'CONFIRMED', :idempotencyKey, :requestHash, :notes,
                        :createdAt, :createdAt)
                    """, params);
        } catch (DataIntegrityViolationException ex) {
            Optional<AppointmentConflictException.Kind> kind = conflictKind(ex);
            if (kind.isPresent()) {
                throw new AppointmentConflictException(kind.get(), ex);
            }
            throw ex;
        }
    }

    private static Optional<AppointmentConflictException.Kind> conflictKind(DataIntegrityViolationException ex) {
        if (!(NestedExceptionUtils.getMostSpecificCause(ex) instanceof PSQLException psql)) {
            return Optional.empty();
        }
        String sqlState = psql.getSQLState();
        ServerErrorMessage message = psql.getServerErrorMessage();
        if (message == null || !(EXCLUSION_VIOLATION.equals(sqlState) || UNIQUE_VIOLATION.equals(sqlState))) {
            return Optional.empty();
        }
        return Optional.ofNullable(CONSTRAINTS.get(message.getConstraint()));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
