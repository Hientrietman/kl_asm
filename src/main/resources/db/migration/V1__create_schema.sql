CREATE TABLE dealership (
    id        uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name      varchar(100) NOT NULL,
    time_zone varchar(64)  NOT NULL,
    active    boolean      NOT NULL DEFAULT true
);

CREATE TABLE dealership_working_hours (
    dealership_id uuid     NOT NULL REFERENCES dealership (id),
    day_of_week   smallint NOT NULL,
    open_time     time     NOT NULL,
    close_time    time     NOT NULL,
    CONSTRAINT pk_dealership_working_hours PRIMARY KEY (dealership_id, day_of_week),
    CONSTRAINT ck_working_hours_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_working_hours_close_after_open CHECK (close_time > open_time)
);

CREATE TABLE service_bay (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    dealership_id uuid        NOT NULL REFERENCES dealership (id),
    code          varchar(50) NOT NULL,
    active        boolean     NOT NULL DEFAULT true,
    CONSTRAINT uq_service_bay_dealership_code UNIQUE (dealership_id, code),
    -- Target of the composite FK from appointment
    CONSTRAINT uq_service_bay_dealership_id UNIQUE (dealership_id, id)
);

CREATE TABLE technician (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    dealership_id uuid         NOT NULL REFERENCES dealership (id),
    full_name     varchar(100) NOT NULL,
    active        boolean      NOT NULL DEFAULT true,
    -- Target of the composite FK from appointment
    CONSTRAINT uq_technician_dealership_id UNIQUE (dealership_id, id)
);

CREATE TABLE service_type (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code             varchar(50)  NOT NULL,
    name             varchar(100) NOT NULL,
    duration_minutes integer      NOT NULL,
    active           boolean      NOT NULL DEFAULT true,
    CONSTRAINT uq_service_type_code UNIQUE (code),
    CONSTRAINT ck_service_type_duration
        CHECK (duration_minutes > 0 AND duration_minutes <= 480 AND duration_minutes % 15 = 0)
);

CREATE TABLE technician_qualification (
    technician_id   uuid NOT NULL REFERENCES technician (id),
    service_type_id uuid NOT NULL REFERENCES service_type (id),
    CONSTRAINT pk_technician_qualification PRIMARY KEY (technician_id, service_type_id)
);

CREATE TABLE customer (
    id        uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name varchar(100) NOT NULL,
    email     varchar(254) NOT NULL,
    phone     varchar(16)
);

CREATE UNIQUE INDEX uq_customer_email_lower ON customer (lower(email));

CREATE TABLE vehicle (
    id    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    vin   varchar(17) NOT NULL,
    make  varchar(50),
    model varchar(50),
    year  smallint,
    CONSTRAINT uq_vehicle_vin UNIQUE (vin),
    CONSTRAINT ck_vehicle_vin CHECK (char_length(vin) = 17 AND vin ~ '^[A-HJ-NPR-Z0-9]{17}$')
);

CREATE TABLE appointment (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    dealership_id       uuid         NOT NULL REFERENCES dealership (id),
    customer_id         uuid         NOT NULL REFERENCES customer (id),
    vehicle_id          uuid         NOT NULL REFERENCES vehicle (id),
    technician_id       uuid         NOT NULL,
    service_bay_id      uuid         NOT NULL,
    service_type_id     uuid         NOT NULL REFERENCES service_type (id),
    start_time          timestamptz  NOT NULL,
    end_time            timestamptz  NOT NULL,
    status              varchar(20)  NOT NULL,
    cancelled_at        timestamptz,
    cancellation_reason varchar(500),
    idempotency_key     varchar(64),
    request_hash        varchar(64),
    notes               varchar(500),
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    CONSTRAINT ck_appointment_time_range CHECK (end_time > start_time),
    CONSTRAINT ck_appointment_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT uq_appointment_idempotency_key UNIQUE (idempotency_key),
    -- Composite FKs guarantee the bay and technician belong to the appointment's dealership
    CONSTRAINT fk_appointment_service_bay
        FOREIGN KEY (dealership_id, service_bay_id) REFERENCES service_bay (dealership_id, id),
    CONSTRAINT fk_appointment_technician
        FOREIGN KEY (dealership_id, technician_id) REFERENCES technician (dealership_id, id)
);

CREATE INDEX ix_appointment_dealership_start ON appointment (dealership_id, start_time);
CREATE INDEX ix_appointment_vehicle ON appointment (vehicle_id);
CREATE INDEX ix_technician_qualification_service_type ON technician_qualification (service_type_id, technician_id);
