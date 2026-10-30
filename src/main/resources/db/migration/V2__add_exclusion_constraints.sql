-- Final authority for the no-double-booking invariant (ADR-003). Cancelled rows never block.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointment
  ADD CONSTRAINT ex_appointment_bay_no_overlap
  EXCLUDE USING gist (
    service_bay_id WITH =,
    tstzrange(start_time, end_time, '[)') WITH &&
  ) WHERE (status = 'CONFIRMED');

ALTER TABLE appointment
  ADD CONSTRAINT ex_appointment_technician_no_overlap
  EXCLUDE USING gist (
    technician_id WITH =,
    tstzrange(start_time, end_time, '[)') WITH &&
  ) WHERE (status = 'CONFIRMED');

ALTER TABLE appointment
  ADD CONSTRAINT ex_appointment_vehicle_no_overlap
  EXCLUDE USING gist (
    vehicle_id WITH =,
    tstzrange(start_time, end_time, '[)') WITH &&
  ) WHERE (status = 'CONFIRMED');
