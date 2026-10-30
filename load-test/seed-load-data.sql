-- Seeds 1,000 customers, 1,000 vehicles, and 10,800 confirmed historical appointments
-- to satisfy NFR-2 (≥ 10,000 seeded appointments for performance testing).
-- Idempotent: safe to run multiple times.

BEGIN;

-- 1. Create customers
INSERT INTO customer (id, full_name, email, phone)
SELECT
    ('c0000000-0000-0000-0001-' || lpad(to_hex(i), 12, '0'))::uuid,
    'LoadTest Customer ' || i,
    'customer_' || i || '@loadtest.example.com',
    '+447911' || lpad(i::text, 6, '0')
FROM generate_series(1, 1000) AS i
ON CONFLICT (id) DO NOTHING;

-- 2. Create vehicles with valid 17-char VINs
INSERT INTO vehicle (id, vin, make, model, year)
SELECT
    ('e0000000-0000-0000-0001-' || lpad(to_hex(i), 12, '0'))::uuid,
    '1HGCR2F8' || lpad(i::text, 9, '0'),
    'Honda',
    'Accord',
    2020
FROM generate_series(1, 1000) AS i
ON CONFLICT (id) DO NOTHING;

-- 3. Create 10,800 confirmed non-overlapping historical appointments (year 2025)
-- 200 days * 9 hourly slots (08:00 to 17:00) * 6 resource pairs (3 bays per dealership * 2 dealerships) = 10,800.
WITH resource_pairs AS (
    SELECT 1 AS pair_idx,
           'd0000000-0000-0000-0000-000000000001'::uuid AS dealership_id,
           'b1000000-0000-0000-0000-000000000001'::uuid AS bay_id,
           'a1000000-0000-0000-0000-000000000001'::uuid AS tech_id
    UNION ALL
    SELECT 2,
           'd0000000-0000-0000-0000-000000000001'::uuid,
           'b1000000-0000-0000-0000-000000000002'::uuid,
           'a1000000-0000-0000-0000-000000000002'::uuid
    UNION ALL
    SELECT 3,
           'd0000000-0000-0000-0000-000000000001'::uuid,
           'b1000000-0000-0000-0000-000000000003'::uuid,
           'a1000000-0000-0000-0000-000000000003'::uuid
    UNION ALL
    SELECT 4,
           'd0000000-0000-0000-0000-000000000002'::uuid,
           'b2000000-0000-0000-0000-000000000001'::uuid,
           'a2000000-0000-0000-0000-000000000001'::uuid
    UNION ALL
    SELECT 5,
           'd0000000-0000-0000-0000-000000000002'::uuid,
           'b2000000-0000-0000-0000-000000000002'::uuid,
           'a2000000-0000-0000-0000-000000000002'::uuid
    UNION ALL
    SELECT 6,
           'd0000000-0000-0000-0000-000000000002'::uuid,
           'b2000000-0000-0000-0000-000000000003'::uuid,
           'a2000000-0000-0000-0000-000000000003'::uuid
),
slots AS (
    SELECT
        d.day_offset,
        h.hour_offset,
        r.pair_idx,
        r.dealership_id,
        r.bay_id,
        r.tech_id,
        (timestamptz '2025-01-01 08:00:00Z' + (d.day_offset || ' days')::interval + (h.hour_offset || ' hours')::interval) AS start_time,
        (timestamptz '2025-01-01 08:00:00Z' + (d.day_offset || ' days')::interval + ((h.hour_offset + 1) || ' hours')::interval) AS end_time,
        ((d.day_offset * 54 + h.hour_offset * 6 + r.pair_idx) % 1000 + 1) AS entity_num
    FROM generate_series(0, 199) AS d(day_offset)
    CROSS JOIN generate_series(0, 8) AS h(hour_offset)
    CROSS JOIN resource_pairs r
)
INSERT INTO appointment (
    id,
    dealership_id,
    customer_id,
    vehicle_id,
    technician_id,
    service_bay_id,
    service_type_id,
    start_time,
    end_time,
    status,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    s.dealership_id,
    ('c0000000-0000-0000-0001-' || lpad(to_hex(s.entity_num), 12, '0'))::uuid,
    ('e0000000-0000-0000-0001-' || lpad(to_hex(s.entity_num), 12, '0'))::uuid,
    s.tech_id,
    s.bay_id,
    'c0000000-0000-0000-0000-000000000001'::uuid, -- OIL_CHANGE (60 min)
    s.start_time,
    s.end_time,
    'CONFIRMED',
    s.start_time,
    s.start_time
FROM slots s
ON CONFLICT DO NOTHING;

COMMIT;
