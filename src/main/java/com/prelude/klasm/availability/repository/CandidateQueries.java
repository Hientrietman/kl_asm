package com.prelude.klasm.availability.repository;

import com.prelude.klasm.availability.service.BayCandidate;
import com.prelude.klasm.availability.service.TechnicianCandidate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only candidate queries (ADR-009). The overlap predicate matches the exclusion constraints, so their GiST
 * indexes serve it. The result is an optimization; the constraints remain the final authority.
 */
@Repository
public class CandidateQueries {

    private final NamedParameterJdbcTemplate jdbc;

    public CandidateQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BayCandidate> freeBays(UUID dealershipId, Instant start, Instant end) {
        return jdbc.query(
                """
                SELECT b.id, b.code
                FROM service_bay b
                WHERE b.dealership_id = :dealershipId
                  AND b.active
                  AND NOT EXISTS (
                      SELECT 1 FROM appointment a
                      WHERE a.service_bay_id = b.id
                        AND a.status = 'CONFIRMED'
                        AND tstzrange(a.start_time, a.end_time, '[)')
                            && tstzrange(CAST(:start AS timestamptz), CAST(:end AS timestamptz), '[)'))
                """,
                new MapSqlParameterSource()
                        .addValue("dealershipId", dealershipId)
                        .addValue("start", utc(start))
                        .addValue("end", utc(end)),
                (rs, row) -> new BayCandidate(rs.getObject("id", UUID.class), rs.getString("code")));
    }

    public List<TechnicianCandidate> freeQualifiedTechnicians(
            UUID dealershipId, UUID serviceTypeId, Instant start, Instant end, Instant dayStart, Instant dayEnd) {
        return jdbc.query(
                """
                SELECT t.id, t.full_name,
                       COALESCE((SELECT SUM(EXTRACT(EPOCH FROM (a.end_time - a.start_time))) / 60
                                 FROM appointment a
                                 WHERE a.technician_id = t.id
                                   AND a.status = 'CONFIRMED'
                                   AND a.start_time >= CAST(:dayStart AS timestamptz)
                                   AND a.start_time < CAST(:dayEnd AS timestamptz)), 0) AS booked_minutes
                FROM technician t
                JOIN technician_qualification q ON q.technician_id = t.id AND q.service_type_id = :serviceTypeId
                WHERE t.dealership_id = :dealershipId
                  AND t.active
                  AND NOT EXISTS (
                      SELECT 1 FROM appointment a
                      WHERE a.technician_id = t.id
                        AND a.status = 'CONFIRMED'
                        AND tstzrange(a.start_time, a.end_time, '[)')
                            && tstzrange(CAST(:start AS timestamptz), CAST(:end AS timestamptz), '[)'))
                """,
                new MapSqlParameterSource()
                        .addValue("dealershipId", dealershipId)
                        .addValue("serviceTypeId", serviceTypeId)
                        .addValue("start", utc(start))
                        .addValue("end", utc(end))
                        .addValue("dayStart", utc(dayStart))
                        .addValue("dayEnd", utc(dayEnd)),
                (rs, row) -> new TechnicianCandidate(
                        rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getLong("booked_minutes")));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    /**
     * FR-5: of the 15-minute starts from {@code first} to {@code last}, those where at least one active bay and one
     * active qualified technician are free for the whole service duration. One set-based query for the whole day.
     */
    public List<Instant> freeStarts(
            UUID dealershipId, UUID serviceTypeId, int durationMinutes, Instant first, Instant last) {
        return jdbc.query(
                """
                SELECT s.start_time
                FROM generate_series(CAST(:first AS timestamptz), CAST(:last AS timestamptz), interval '15 minutes')
                     AS s(start_time)
                WHERE EXISTS (
                        SELECT 1 FROM service_bay b
                        WHERE b.dealership_id = :dealershipId
                          AND b.active
                          AND NOT EXISTS (
                              SELECT 1 FROM appointment a
                              WHERE a.service_bay_id = b.id
                                AND a.status = 'CONFIRMED'
                                AND tstzrange(a.start_time, a.end_time, '[)')
                                    && tstzrange(s.start_time,
                                                 s.start_time + CAST(:durationMinutes AS integer) * interval '1 minute',
                                                 '[)')))
                  AND EXISTS (
                        SELECT 1 FROM technician t
                        JOIN technician_qualification q
                          ON q.technician_id = t.id AND q.service_type_id = :serviceTypeId
                        WHERE t.dealership_id = :dealershipId
                          AND t.active
                          AND NOT EXISTS (
                              SELECT 1 FROM appointment a
                              WHERE a.technician_id = t.id
                                AND a.status = 'CONFIRMED'
                                AND tstzrange(a.start_time, a.end_time, '[)')
                                    && tstzrange(s.start_time,
                                                 s.start_time + CAST(:durationMinutes AS integer) * interval '1 minute',
                                                 '[)')))
                ORDER BY s.start_time
                """,
                new MapSqlParameterSource()
                        .addValue("dealershipId", dealershipId)
                        .addValue("serviceTypeId", serviceTypeId)
                        .addValue("durationMinutes", durationMinutes)
                        .addValue("first", utc(first))
                        .addValue("last", utc(last)),
                (rs, row) -> rs.getObject("start_time", OffsetDateTime.class).toInstant());
    }
}
