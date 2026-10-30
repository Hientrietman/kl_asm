# Requirements & Architecture Decisions

## Scenario A: The Unified Service Scheduler

> An Appointment Scheduler application to replace manual booking systems for automotive dealerships.

Document version: 2.0 (revised against the Keyloop assessment brief, Scenario A)

---

## Table of Contents

- [Traceability to the Assessment Brief](#traceability-to-the-assessment-brief)
- [Domain Model and Data Constraints](#domain-model-and-data-constraints)
- [Functional Requirements](#functional-requirements)
- [API Conventions and Error Contract](#api-conventions-and-error-contract)
- [Non-Functional Requirements](#non-functional-requirements)
- [Assumptions](#assumptions)
- [Out of Scope](#out-of-scope)
- [Architecture Decision Records (ADRs)](#architecture-decision-records-adrs)
- [Glossary](#glossary)

---

## Traceability to the Assessment Brief

| Brief item | Covered by |
|-----------|-----------|
| CR1: Resource Constrained Booking (vehicle, service type, dealership, desired time) | FR-1, FR-6 |
| CR2: Real-Time Availability Check (ServiceBay + qualified Technician, entire duration) | FR-2, FR-5, NFR-1 |
| CR3: Confirmed Appointment Record (customer, vehicle, technician, service bay) | FR-3, NFR-7 |
| RESTful API + persistent database (backend path) | FR-1 to FR-6, ADR-001, ADR-005, ADR-011 |
| Stub the client layer (cURL / OpenAPI) | NFR-5, README examples |
| Build for the future: scalability | NFR-9, ADR-002 |
| Build for the future: performance | NFR-2, ADR-003 |
| Build for the future: reliability | NFR-8, ADR-010 |
| Build for the future: maintainability | NFR-10, ADR-002, ADR-005 |
| Build for the future: observability (logging, metrics, tracing) | NFR-3, ADR-006 |
| Tests that validate core business logic | NFR-4, ADR-012 |
| Ambiguities documented as assumptions | Assumptions A-1 to A-17 |

---

## Domain Model and Data Constraints

All primary keys are UUIDs. All timestamps are `timestamptz` (stored in UTC).

| Entity | Fields and constraints |
|--------|------------------------|
| `dealership` | `id`, `name` (NOT NULL, max 100), `time_zone` (NOT NULL, IANA id such as `Europe/London`), `active` (NOT NULL) |
| `dealership_working_hours` | `dealership_id` (FK), `day_of_week` (1-7, ISO), `open_time`, `close_time` (CHECK `close_time > open_time`), UNIQUE (`dealership_id`, `day_of_week`). A missing row means the dealership is closed that day. |
| `service_bay` | `id`, `dealership_id` (FK), `code` (NOT NULL), `active`; UNIQUE (`dealership_id`, `code`); UNIQUE (`dealership_id`, `id`) to support composite FKs |
| `technician` | `id`, `dealership_id` (FK), `full_name`, `active`; UNIQUE (`dealership_id`, `id`) |
| `service_type` | `id`, `code` (UNIQUE), `name`, `duration_minutes` (CHECK `> 0`, `<= 480`, multiple of 15), `active` |
| `technician_qualification` | PK (`technician_id`, `service_type_id`), both FKs |
| `customer` | `id`, `full_name`, `email` (UNIQUE on `lower(email)`), `phone` (nullable) |
| `vehicle` | `id`, `vin` (UNIQUE, CHECK length 17 and pattern `^[A-HJ-NPR-Z0-9]{17}$`), `make`, `model`, `year` (nullable) |
| `appointment` | `id`, `dealership_id`, `customer_id`, `vehicle_id`, `technician_id`, `service_bay_id`, `service_type_id` (all FK, NOT NULL); `start_time`, `end_time` (NOT NULL, CHECK `end_time > start_time`); `status` (NOT NULL, CHECK in `CONFIRMED`, `CANCELLED`); `cancelled_at`, `cancellation_reason`; `idempotency_key` (nullable, UNIQUE); `request_hash`; `notes`; `created_at`, `updated_at` (NOT NULL). Composite FKs (`dealership_id`, `service_bay_id`) and (`dealership_id`, `technician_id`) guarantee the resources belong to the appointment's dealership. Three exclusion constraints (bay, technician, vehicle), see ADR-003. |

---

## Functional Requirements

Priority: **MUST** = required by the brief or by the core invariant; **SHOULD** = strongly recommended, implemented if time allows.

### FR-1: Resource Constrained Booking (MUST)

`POST /api/v1/appointments` creates a booking. The caller provides only business information; the system decides which service bay and technician to use (see FR-2).

| Field | Type | Required | Constraint |
|-------|------|----------|-----------|
| `dealershipId` | UUID | Yes | Must reference an existing, active dealership |
| `serviceTypeId` | UUID | Yes | Must reference an existing, active service type |
| `startTime` | ISO-8601 date-time with offset (e.g. `2026-11-03T09:30:00Z`) | Yes | Must lie on the 15-minute grid (minutes in 0, 15, 30, 45; seconds and fractions equal to 0) |
| `customer.fullName` | string | Yes | 1-100 characters after trim; no control characters |
| `customer.email` | string | Yes | Valid email, max 254 characters, normalized to lower case |
| `customer.phone` | string | No | E.164 format, pattern `^\+[1-9]\d{6,14}$` |
| `vehicle.vin` | string | Yes | After trim and upper-casing: exactly 17 characters matching `^[A-HJ-NPR-Z0-9]{17}$` (letters I, O, Q are not allowed) |
| `vehicle.make`, `vehicle.model` | string | No | Max 50 characters each |
| `vehicle.year` | integer | No | Between 1981 and (current year + 1) |
| `serviceBayId` | UUID | No | Optional override; see FR-2.4 |
| `technicianId` | UUID | No | Optional override; see FR-2.4 |
| `notes` | string | No | Max 500 characters |

Header: `Idempotency-Key` (optional, 1-64 characters). See FR-3.4.

- **FR-1.1** The system calculates `end_time = start_time + service_type.duration_minutes`.
- **FR-1.2** Customer is resolved by case-insensitive email; vehicle is resolved by VIN. If either does not exist it is created. If it exists, the stored record is reused and is **not** modified by the booking request. Customer and vehicle resolution happen in the same transaction as the appointment insert.
- **FR-1.3** Time rules, evaluated in the dealership's time zone:
  - `startTime` must be at least `booking.min-lead-time` (default 60 minutes) in the future and at most `booking.max-horizon` (default 90 days) ahead.
  - The whole range `[start_time, end_time)` must fall inside the dealership's working hours of a single local day. Appointments cannot span midnight or a closed day.
- **FR-1.4** A request that violates field constraints returns `400`; a request that is well-formed but violates a business rule returns `422` (see the error catalog).

### FR-2: Real-Time Availability Check and Resource Assignment (MUST)

Before an appointment is confirmed, the system must find a **service bay** and a **qualified technician** that are both free for the entire `[start_time, end_time)` range.

- **FR-2.1 Interval semantics.** Time ranges are half-open: `[start, end)`. An appointment ending at 10:00 does not conflict with one starting at 10:00.
- **FR-2.2 Blocking statuses.** Only appointments with status `CONFIRMED` block a resource. `CANCELLED` appointments never block.
- **FR-2.3 Candidate resources.** A bay is a candidate when it is active and belongs to the requested dealership. A technician is a candidate when they are active, belong to the requested dealership, and are qualified for the requested service type. A resource is available when no `CONFIRMED` appointment of that resource overlaps the requested range.
- **FR-2.4 Auto-assignment (default).** When `serviceBayId` and/or `technicianId` are omitted, the system assigns them deterministically:
  - Bay: the available bay with the lowest `code`.
  - Technician: the available qualified technician with the fewest booked minutes on that local day; ties broken by `id`.
  - If the caller supplies one of them, only the missing one is auto-assigned.
- **FR-2.5 Explicit override (optional).** If the caller supplies a bay or technician, the system validates that it belongs to the dealership, that the technician is qualified, and that it is free. Failures return `422` (mismatch, not qualified) or `409` (occupied).
- **FR-2.6 Vehicle rule.** A vehicle cannot have two overlapping `CONFIRMED` appointments, regardless of dealership.
- **FR-2.7 Conflict response.** When no assignment is possible the system returns `409 Conflict` with a Problem Details body (see error contract) containing the conflict code, the resource type involved and, when auto-assigning, up to 3 alternative start times on the same day (SHOULD).
- **FR-2.8 Race safety.** The pre-check is an optimization only. The database exclusion constraints (ADR-003) are the final authority. If an insert fails with SQLSTATE `23P01` during auto-assignment, the system retries with the next candidate, up to 3 attempts, before returning `409`.

### FR-3: Confirmed Appointment Record (MUST)

- **FR-3.1** On success the system persists an `Appointment` linking customer, vehicle, technician, service bay, service type and dealership, with `start_time`, calculated `end_time`, `status = CONFIRMED`, and `created_at` / `updated_at`.
- **FR-3.2** Response: `201 Created`, `Location: /api/v1/appointments/{id}`, body containing the appointment id, the assigned technician and bay, the times in UTC, and the dealership time zone.
- **FR-3.3** Persistence is atomic: customer/vehicle resolution and appointment insert succeed or fail together.
- **FR-3.4 Idempotency.** If `Idempotency-Key` is supplied:
  - Same key and same request body (compared by `request_hash`): return the originally created appointment with `200 OK` and no new record.
  - Same key and different body: `422` with code `IDEMPOTENCY_KEY_MISMATCH`.
  - Concurrent requests with the same key: the unique constraint on `idempotency_key` ensures one insert; the loser re-reads and returns the stored result.
  - Failed attempts (4xx/409) are not stored, so the client may retry with the same key.

### FR-4: Appointment Management (SHOULD)

- **FR-4.1** `GET /api/v1/appointments/{id}` returns the appointment or `404`.
- **FR-4.2** `GET /api/v1/appointments` lists appointments. Optional filters: `dealershipId`, `from` and `to` (applied to `start_time`, `from` inclusive, `to` exclusive), `status`, `vin`. Pagination: `page` (default 0) and `size` (default 20, max 100). Default sort: `startTime` ascending.
- **FR-4.3** `POST /api/v1/appointments/{id}/cancel` with optional body `{ "reason": "..." }` sets `status = CANCELLED`, `cancelled_at`, `cancellation_reason`. Cancelling an already cancelled appointment is idempotent and returns `200` with the current state. Cancelling an appointment whose `start_time` is in the past returns `422` (`APPOINTMENT_NOT_CANCELLABLE`).
- **FR-4.4** Cancelling frees the bay, technician and vehicle immediately, because only `CONFIRMED` rows are covered by the exclusion constraints.

### FR-5: Availability Query (SHOULD)

`GET /api/v1/dealerships/{dealershipId}/availability?serviceTypeId={id}&date={yyyy-MM-dd}` returns the start times (in UTC and in dealership local time) on that local date for which at least one bay and one qualified technician are free. The result is informational and is not a reservation; a subsequent booking can still return `409`.

### FR-6: Reference Data Lookup (SHOULD)

Read-only endpoints so a client or Swagger UI user can discover valid ids from seed data: `GET /api/v1/dealerships`, `GET /api/v1/service-types`, `GET /api/v1/dealerships/{id}/service-bays`, `GET /api/v1/dealerships/{id}/technicians`.

---

## API Conventions and Error Contract

- Base path `/api/v1`; JSON only; field names in camelCase; timestamps in ISO-8601 with offset.
- Errors use `application/problem+json` (RFC 9457) with extension members `code`, `traceId`, and, where relevant, `errors[]` (field violations) or `conflict` (resource details).
- Every response carries the trace id in the `X-Trace-Id` header.

| HTTP | `code` | When |
|------|--------|------|
| 400 | `VALIDATION_ERROR` | Malformed JSON or a field violates its constraint (VIN, email, time grid, missing field) |
| 404 | `NOT_FOUND` | Dealership, service type, bay, technician or appointment does not exist |
| 409 | `RESOURCE_CONFLICT` | Explicitly supplied bay or technician is occupied |
| 409 | `NO_AVAILABILITY` | Auto-assignment found no free bay/technician pair |
| 409 | `VEHICLE_ALREADY_BOOKED` | Vehicle has an overlapping confirmed appointment |
| 422 | `OUTSIDE_WORKING_HOURS` | Range is not fully inside the dealership's working hours, or the day is closed |
| 422 | `INVALID_BOOKING_WINDOW` | `startTime` is before the minimum lead time or beyond the maximum horizon |
| 422 | `TECHNICIAN_NOT_QUALIFIED` | Supplied technician cannot perform the service type, or no qualified technician exists at the dealership |
| 422 | `RESOURCE_DEALERSHIP_MISMATCH` | Supplied bay or technician belongs to another dealership |
| 422 | `IDEMPOTENCY_KEY_MISMATCH` | Same key reused with a different request body |
| 422 | `APPOINTMENT_NOT_CANCELLABLE` | Appointment already started or finished |
| 500 | `INTERNAL_ERROR` | Unexpected failure; no stack trace or SQL in the body |

---

## Non-Functional Requirements

| ID | Requirement | Target | Verification |
|----|-------------|--------|--------------|
| NFR-1 | **Consistency** | No double-booking of a bay, technician or vehicle, even under concurrent requests | Integration test: 20 parallel requests for the same slot and resources produce exactly one `201` and nineteen `409`; same test for auto-assignment |
| NFR-2 | **Response Time** | p95 below 500 ms for create, get and cancel at 20 concurrent users with at least 10,000 seeded appointments | Load script (k6 or Gatling) documented in the README |
| NFR-3 | **Observability** | Logging: structured JSON logs with `traceId` and `spanId` on every line, no PII (email and phone masked). Metrics: Micrometer exposed at `/actuator/prometheus` with custom `appointments_booked_total`, `booking_conflicts_total{reason}` and a booking latency timer. Tracing: OpenTelemetry via Micrometer Tracing, W3C `traceparent` propagation. Health: liveness and readiness probes | Log sample and metrics scrape in the README; test asserting the `X-Trace-Id` header |
| NFR-4 | **Testability** | Unit tests for assignment logic, time/working-hour validation and VIN validation; integration tests on a real PostgreSQL (Testcontainers); at least 80% line coverage on the service layer | JaCoCo report in CI or local build |
| NFR-5 | **API Documentation** | OpenAPI 3 spec generated from code, Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`, request examples and the error catalog documented | Spec exported and committed as `openapi.yaml` |
| NFR-6 | **Containerization** | `docker compose up` starts PostgreSQL and the app, runs migrations and seed data, no manual step; containers have health checks | Fresh-clone smoke test |
| NFR-7 | **Data Integrity** | Foreign keys, NOT NULL, CHECK, UNIQUE and exclusion constraints as listed in the domain model; schema changes only through versioned migrations | Migration tests; constraint-violation tests |
| NFR-8 | **Reliability** | Idempotent booking, bounded retry on exclusion violation, DB statement and connection timeouts, graceful shutdown | Failure-path tests; configuration review |
| NFR-9 | **Scalability** | Application instances are stateless and can be scaled horizontally behind a load balancer; all coordination happens in PostgreSQL; hot queries are served by the GiST indexes created by the exclusion constraints and by B-tree indexes on filter columns | Design document section; `EXPLAIN` of the availability query |
| NFR-10 | **Maintainability** | Package-by-feature modules with layered structure, dependency rules checked by an architecture test, formatter and static checks in the build | Build passes with checks enabled |
| NFR-11 | **Security baseline** | No authentication (A-5), but: bean validation on all input, parameterized queries only, no internal details in error responses, no PII in logs | Code review checklist; error-response tests |

---

## Assumptions

> These assumptions were made where the brief was ambiguous. Each is documented here for transparency. Items marked CHANGED differ from version 1.

| ID | Assumption | Rationale |
|----|-----------|-----------|
| A-1 | **Single dealership context per request.** Each request operates within one dealership. | The brief says "a specific dealership". Multi-dealership search is out of scope. |
| A-2 | **CHANGED: Service bay and technician are assigned by the system.** The user supplies vehicle, service type, dealership and desired time. `serviceBayId` and `technicianId` are optional overrides only. | The brief (CR2) makes the system responsible for checking availability of a ServiceBay and a qualified Technician; it never asks the user to choose them. |
| A-3 | **CHANGED: Dealership working hours are enforced.** Each dealership has one open interval per weekday in its local time zone. No holidays, breaks or lunch closures. | Without it, auto-assignment could confirm appointments at 03:00. Working hours are seeded reference data. |
| A-4 | **CHANGED: 15-minute booking grid.** `startTime` must be on a 15-minute boundary and service durations are multiples of 15 minutes. | The brief mentions slot granularity as an open point. A grid keeps availability queries finite and avoids fragmented gaps, while range-based overlap checks still apply. |
| A-5 | **No authentication/authorization.** The "user" in the brief is the caller of the API and is not modeled; the customer is described in the request body. | Auth is orthogonal to scheduling logic. Production would use JWT/OAuth2. |
| A-6 | **Reference data is pre-seeded.** Dealerships, working hours, bays, technicians, service types and technician qualifications are loaded via migrations/seed scripts. | CRUD for reference data is not part of the core requirements. FR-6 exposes it read-only. |
| A-7 | **CHANGED: UTC storage plus dealership time zone.** Timestamps are stored as `timestamptz` (UTC) and exchanged with an explicit offset. Each dealership has an IANA time zone used only to evaluate working hours and local-day boundaries. | UTC storage avoids ambiguity; the zone is still required because working hours are local and daylight saving time shifts offsets. |
| A-8 | **No recurring appointments.** Each appointment is a single event. | Out of scope for the brief. |
| A-9 | **Cancellation is a soft delete.** Cancelled rows remain with status `CANCELLED` and stop blocking resources because the exclusion constraints apply only to `CONFIRMED` rows. | Preserves audit history while freeing capacity. |
| A-10 | **Resources belong to exactly one dealership.** Technicians are available during all working hours of their dealership; individual shifts and leave are not modeled. | Keeps the availability model to appointments only. |
| A-11 | **One bay and one technician per appointment, for the whole duration.** No setup or cleaning buffer between appointments. | Matches CR2 wording ("entire service duration"). |
| A-12 | **Service duration is fixed per service type** and is the same in every dealership. | The brief gives no duration data. Per-dealership overrides are a possible extension. |
| A-13 | **Customer identity is the email address; vehicle identity is the VIN.** Booking never updates an existing customer or vehicle. The VIN check digit is not verified (it is mandated only for North American vehicles); only length and allowed characters are validated. | Avoids silent overwrites and false rejections of valid non-North-American VINs. |
| A-14 | **A vehicle can be in only one place at a time.** Overlapping confirmed appointments for the same VIN are rejected. | Physical constraint that the original model did not capture. |
| A-15 | **Booking window defaults.** Minimum lead time 60 minutes, maximum horizon 90 days, both configurable. | Prevents bookings in the past and unbounded far-future bookings. |
| A-16 | **Appointments do not span midnight.** The whole range must fit inside one local working day. | Follows from A-3 and a single open interval per day. |
| A-17 | **Idempotency keys live as long as the appointment row.** No separate retention window. | Simplest correct behavior; a key table with TTL is a possible extension. |

---

## Out of Scope

The following features are **not implemented** but are acknowledged as natural extensions:

| Feature | Why Out of Scope |
|---------|-----------------|
| **User authentication & authorization** | Orthogonal to scheduling logic; would use standard JWT/OAuth2 in production |
| **Notification system** (email/SMS) | Would be an event-driven extension; noted in design as a future domain event consumer |
| **Recurring appointments** | Complex scheduling logic beyond core requirements |
| **Holidays, breaks and technician shifts** | Would add an availability-calendar model; working hours per weekday are enough for the core constraint |
| **Setup/cleaning buffers and multi-bay services** | Extends the resource model beyond one bay and one technician |
| **Optimal assignment (solver-based)** | The deterministic heuristic in FR-2.4 is sufficient; global optimization is a separate problem |
| **Appointment modification (reschedule)** | Equivalent to cancel plus rebook (not atomic in this version); cancel is implemented |
| **Waiting list** | Would need a queue and notification support |
| **AI assistant for availability lookup** | Optional extension (function calling over the availability query); postponed |
| **Frontend UI** | Backend-focused implementation; OpenAPI and Swagger UI serve as the client interface |
| **Rate limiting** | Production concern; would use an API gateway or middleware |
| **Caching** | Not needed at assessment scale; would consider Redis for availability lookups in production |
| **CI/CD pipeline** | Would use a managed pipeline (for example GitHub Actions or AWS CodePipeline); Dockerfile provided for containerization |
| **Payment/pricing** | Business domain extension, not part of scheduling core |

---

## Architecture Decision Records (ADRs)

### ADR-001: PostgreSQL as the Primary Database

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | We need a database that supports complex scheduling constraints, specifically preventing time range overlaps at the data level, and that guarantees durability for confirmed appointments. |
| **Decision** | Use PostgreSQL 16 with the `btree_gist` extension for exclusion constraints on time ranges. |
| **Alternatives Considered** | **MySQL** has no native exclusion constraints or range types; overlap prevention would need application-level locking or a slot-per-row table. **MongoDB** supports multi-document ACID transactions, but has no range type or exclusion constraint, so non-overlap would again depend on application logic. **SQLite** has no exclusion constraints and serializes all writes, which does not fit a multi-instance service. **Slot table (one row per resource per 15-minute slot with a UNIQUE key)** works on any database, but multiplies rows, complicates multi-slot atomic inserts and ties the schema to the grid size. |
| **Consequences** | Positive: database-level guarantee against double-booking, independent of application bugs and instance count. `tstzrange` gives first-class time range support. Mature, well-documented, widely adopted. Negative: requires the `btree_gist` extension (a trusted extension since PostgreSQL 13, so it can be created by the database owner). Ties the solution to PostgreSQL-specific features. |

---

### ADR-002: Modular Monolith Architecture

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | We need an architecture that balances clean code organization with development simplicity for a single-team, single-domain application. |
| **Decision** | Structure the application as a modular monolith deployed as a single process. Modules are packaged by feature: `appointment`, `availability`, `dealership` (dealerships, bays, technicians, qualifications, working hours), `customer` (customers and vehicles) and `catalog` (service types). Inside each module the structure is layered: controller, service, repository. A module may call another module only through its public service interface, never through its repositories or entities. The rule is enforced by an architecture test (ArchUnit). |
| **Alternatives Considered** | **Microservices** introduce network overhead, distributed transaction complexity and operational burden that are unjustified for this scope. **Traditional monolith without module boundaries** leads to tangled dependencies and makes future extraction difficult. **Hexagonal / Clean Architecture** isolates the domain more strongly, but adds ports, adapters and mapping layers that are disproportionate for this size. **Spring Modulith** formalizes module boundaries and could be adopted later; it is not used now to keep dependencies minimal. |
| **Consequences** | Positive: simple deployment (single container), no network latency between modules, a single database transaction for booking (no saga or two-phase commit), module boundaries make future extraction straightforward. Negative: all modules share one process, so a defect in one can affect the others; boundaries rely on discipline and the architecture test rather than the compiler. |

---

### ADR-003: Exclusion Constraints for Overlap Prevention

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | The core business rule is that a service bay, a technician and a vehicle cannot be double-booked. Two concurrent requests for the same slot must result in exactly one success. |
| **Decision** | Use three PostgreSQL exclusion constraints (`EXCLUDE USING gist`) on the `appointment` table, one per resource column (service bay, technician, vehicle). Each combines equality on the resource id with overlap (`&&`) on `tstzrange(start_time, end_time, '[)')`, restricted by a partial predicate `WHERE (status = 'CONFIRMED')` so cancelled appointments do not block. |
| **Alternatives Considered** | **Check-then-insert in the application** has a time-of-check to time-of-use (TOCTOU) race window. **Optimistic locking with a version column** protects updates to an existing row, not the insertion of a new overlapping row, so it does not apply here. **Pessimistic locking (`SELECT ... FOR UPDATE`)** cannot lock rows that do not exist yet, so it would have to lock the parent resource rows, serializing all bookings for a bay or technician and reducing throughput. **Advisory locks** require the application to manage lock keys and lifecycle correctly. **SERIALIZABLE isolation** is correct but adds predicate-lock overhead and requires retry handling for serialization failures. |
| **Consequences** | Positive: the database engine guarantees that no two confirmed appointments overlap for the same resource, even under concurrent transactions and multiple application instances. The constraints create GiST indexes that also speed up availability queries. The database is the single source of truth for the invariant. Negative: requires `btree_gist`. A violation surfaces as SQLSTATE `23P01` (`exclusion_violation`) and must be caught and translated into `409 Conflict`, using the constraint name to decide which resource conflicted. |
| **Implementation** | Flyway migration `V1__create_schema.sql` (tables) and `V2__add_exclusion_constraints.sql` (constraints below). |

```sql
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
```

---

### ADR-004: Spring Boot as the Application Framework

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | We need a Java framework that supports a modular architecture, dependency injection, declarative transactions, input validation, API documentation and built-in production features (health checks, metrics, structured logging). |
| **Decision** | Use Java 17 (LTS) with Spring Boot 3.4 or later (3.4 introduced built-in structured logging). Supporting libraries: Spring Web (MVC), Jakarta Bean Validation, Spring Data JPA, Spring Boot Actuator, Micrometer, springdoc-openapi. |
| **Alternatives Considered** | **Quarkus** and **Micronaut** offer faster startup and lower memory use, but have a smaller ecosystem and community. **Jakarta EE on an application server** is heavier to configure and run for a single service. |
| **Consequences** | Positive: constructor-based dependency injection gives testable, mockable services. Package-by-feature modules map naturally to the modular monolith. Annotation-based validation (`@Valid`, `@NotNull`, custom constraint annotations such as `@Vin`), declarative transactions (`@Transactional`) and OpenAPI generation reduce boilerplate. Actuator and Micrometer provide health, metrics and tracing out of the box. Large ecosystem and community. Negative: annotation-driven auto-configuration can hide behavior (for example `@Transactional` is proxy-based, so self-invocation bypasses it); the team must understand these pitfalls. |

---

### ADR-005: JPA for Database Access

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | We need data access that integrates well with Spring Boot, supports PostgreSQL-specific features (range types, exclusion constraints) and provides schema migration tooling. |
| **Decision** | Use Spring Data JPA (Hibernate as the JPA provider) for entity persistence and simple queries, native SQL queries for PostgreSQL-specific logic (candidate search with `NOT EXISTS` and range overlap), and Flyway for versioned schema migrations. Flyway owns the schema: `spring.jpa.hibernate.ddl-auto=validate`, and `spring.jpa.open-in-view=false`. |
| **Alternatives Considered** | **jOOQ** gives type-safe SQL and first-class access to database-specific features, but adds code generation and a second query style. **JdbcTemplate / MyBatis** give full SQL control but require more mapping code for simple CRUD. **Hibernate auto DDL generation** cannot express exclusion constraints and is not recommended for production schema management. |
| **Consequences** | Positive: first-class Spring Boot integration for both JPA and Flyway. Migrations give auditable, repeatable schema versioning. Native queries keep PostgreSQL-specific features available without leaving the framework. Entities do not need to map `tstzrange`, because the range expression lives inside the constraint and queries. Negative: two query styles (derived/JPQL and native SQL) must be kept consistent; native queries are not checked at compile time, so they require integration tests against a real PostgreSQL. |

---

### ADR-006: Structured JSON Logging with Spring Boot and Logback

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | The brief asks for an observability strategy. Logs must be machine-parseable and each line must be traceable to a request across components, without leaking personal data. |
| **Decision** | Use Spring Boot's native structured logging (Logback backend) with `logging.structured.format.console=ecs` (Elastic Common Schema) in the `docker` profile and human-readable logs in the `local` profile. Distributed tracing is provided by Micrometer Tracing with the OpenTelemetry bridge; `traceId` and `spanId` are placed in the MDC and therefore appear on every log line. The trace id is returned to clients in the `X-Trace-Id` header and in the `traceId` field of error responses. Log fields for booking events include `appointmentId`, `dealershipId`, `serviceTypeId` and the outcome code; email, phone and notes are never logged (VIN is logged only truncated to its last 6 characters). |
| **Alternatives Considered** | **Pino** is a Node.js library and is not applicable to this stack. **logstash-logback-encoder** is a mature third-party option that remains valid for Spring Boot versions before 3.4, but adds a dependency. **Plain-text logs with a pattern layout** are harder to query and correlate. |
| **Consequences** | Positive: no extra logging dependency, standard schema compatible with common log platforms, automatic inclusion of MDC entries, consistent correlation between logs, traces and error responses. Negative: requires Spring Boot 3.4 or later; JSON output is less readable locally, hence the separate `local` profile. |

---

### ADR-007: Docker Compose for Local Development

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | Reviewers must be able to run the whole system with a single command and no manual setup (NFR-6). |
| **Decision** | Provide a `docker-compose.yml` with: `db` (`postgres:16-alpine`, named volume, `pg_isready` health check) and `app` (built from a multi-stage `Dockerfile` using a Temurin 17 JRE image, running as a non-root user, `depends_on` the database with `condition: service_healthy`, health check on `/actuator/health/readiness`). Configuration comes from environment variables with an `.env.example`. Flyway runs migrations and loads seed data on application start. An optional Compose profile `observability` adds an OpenTelemetry collector, Prometheus and Grafana (or Jaeger) for the metrics and tracing demo. |
| **Alternatives Considered** | **Manual installation of PostgreSQL and Java** is error-prone and violates NFR-6. **Spring Boot Docker Compose support** is convenient for development but does not package the app image. **Kubernetes (kind/minikube)** is unnecessary overhead for a single service. **Buildpacks (`spring-boot:build-image`)** are valid, but a Dockerfile makes the build steps explicit and reviewable. |
| **Consequences** | Positive: reproducible environment, one-command start, the same image can be deployed to any container platform, the observability stack is opt-in. Negative: requires Docker; image build time is longer on the first run; seed data runs on every fresh volume only. |

---

### ADR-008: Time Handling (UTC Storage, Dealership Time Zone, 15-Minute Grid)

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | Working hours are local to a dealership, appointments must be unambiguous across clients, and unrestricted start times would produce fragmented availability. |
| **Decision** | Store all instants as `timestamptz` (UTC) and exchange them as ISO-8601 with an explicit offset. Store an IANA time zone per dealership and convert with `java.time.ZonedDateTime` only to evaluate working hours and local-day boundaries. Restrict start times and durations to a 15-minute grid. Inject a `Clock` bean so time-dependent rules are testable. |
| **Alternatives Considered** | **Local timestamps without offset** are ambiguous around daylight saving transitions. **UTC only with no dealership zone** cannot express "open 08:00 to 17:00 local". **1-minute granularity** adds flexibility but produces unusable gaps and unbounded availability results. |
| **Consequences** | Positive: unambiguous instants, correct behavior across daylight saving changes, finite and predictable availability. Negative: requires seed data for time zones and working hours; clients must send an offset. |

---

### ADR-009: Resource Auto-Assignment Strategy

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | The brief requires the system to check availability of a bay and a qualified technician; the caller does not choose them (A-2). |
| **Decision** | Select candidates with one native SQL query per resource type (active, same dealership, qualified for technicians, `NOT EXISTS` overlapping confirmed appointment), ordered by the deterministic rules in FR-2.4. Insert the appointment; if the insert fails with `23P01`, retry with the next candidate, up to 3 attempts. |
| **Alternatives Considered** | **Locking candidate resource rows with `SELECT ... FOR UPDATE SKIP LOCKED`** reduces retries but adds lock handling and still needs the constraints as a safety net. **A solver or optimizer** (global utilization) is over-engineering for this scope. **Random selection** is not reproducible and harder to test. |
| **Consequences** | Positive: deterministic and unit-testable, simple, correctness never depends on the heuristic because the constraints are the safety net. Negative: the heuristic may not maximize utilization; high contention can produce `409` even when another combination would have worked after more retries. |

---

### ADR-010: Idempotent Booking

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | Clients may retry a booking after a timeout; without protection this creates duplicate appointments or a misleading `409`. |
| **Decision** | Accept an optional `Idempotency-Key` header. Store the key and a SHA-256 hash of the normalized request body on the appointment row, with a UNIQUE constraint on the key. A repeated key with the same hash returns the original result; a different hash returns `422 IDEMPOTENCY_KEY_MISMATCH`. |
| **Alternatives Considered** | **Separate idempotency table with TTL** supports caching error responses and expiry, but is more machinery than needed here. **No idempotency** leaves retry behavior undefined. **Natural-key deduplication** (same vehicle and start time) cannot distinguish a retry from a legitimate second request. |
| **Consequences** | Positive: safe retries, race-safe through the unique constraint, no extra table. Negative: only successful bookings are remembered; keys are never expired. |

---

### ADR-011: REST API Conventions and Error Format

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | Clients need predictable, machine-readable errors, and the API must be able to evolve. |
| **Decision** | Version the API in the path (`/api/v1`). Use standard HTTP status codes (400, 404, 409, 422) as defined in the error catalog, and return errors as RFC 9457 Problem Details using Spring's `ProblemDetail` support and a global `@RestControllerAdvice`. Add a stable machine-readable `code` extension member. |
| **Alternatives Considered** | **Custom error envelope** works but is non-standard. **Header or media-type versioning** is valid but harder to try in Swagger UI and cURL. **Using only 400 and 409** loses the distinction between malformed input and business rule violations. |
| **Consequences** | Positive: standard format with framework support, easy client handling via `code`, clear separation of syntactic and semantic errors. Negative: path versioning means a new base path for breaking changes. |

---

### ADR-012: Testing Strategy

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | The brief requires tests that validate the core business logic, and NFR-1 depends on real database behavior. |
| **Decision** | Three layers: (1) unit tests with JUnit 5 and Mockito for assignment logic, time and working-hour validation and the VIN validator; (2) integration tests with Spring Boot Test and Testcontainers running the same PostgreSQL version as production, including a concurrency test that fires parallel booking requests; (3) API tests with MockMvc or REST Assured covering the error catalog. Coverage measured by JaCoCo. |
| **Alternatives Considered** | **H2 in PostgreSQL mode** does not support exclusion constraints or range types, so it cannot verify the central invariant. **Mocking the repository only** cannot detect constraint or SQL errors. |
| **Consequences** | Positive: tests exercise the real constraints and migrations, high confidence in NFR-1. Negative: integration tests are slower and need Docker. |

---

## Glossary

| Term | Definition |
|------|-----------|
| **Appointment** | A confirmed booking of a service bay and technician for a specific vehicle at a specific time |
| **Service Bay** | A physical workspace in a dealership where vehicle servicing occurs |
| **Technician** | A dealership employee qualified to perform specific service types |
| **Service Type** | A category of vehicle service (e.g., Oil Change, Brake Inspection) with a fixed duration |
| **VIN** | Vehicle Identification Number: a unique 17-character identifier for a vehicle; the letters I, O and Q are not used |
| **Working Hours** | The local-time interval during which a dealership accepts appointments on a given weekday |
| **Booking Grid** | The 15-minute boundary to which start times and durations are aligned |
| **Half-open Interval** | A range `[start, end)` that includes its start and excludes its end, so back-to-back appointments do not overlap |
| **Auto-assignment** | The system choosing the service bay and technician for a booking request |
| **Idempotency Key** | A client-supplied token that makes a retried request return the original result instead of creating a duplicate |
| **Trace ID** | Identifier propagated across logs, traces and responses to correlate one request end to end |
| **Problem Details** | The standard JSON error format defined by RFC 9457 (`application/problem+json`) |
| **Exclusion Constraint** | A PostgreSQL constraint that prevents rows whose specified columns satisfy given operators (such as equality and overlap) at the same time |
| **SQLSTATE 23P01** | The PostgreSQL error code for an exclusion constraint violation |
| **tstzrange** | PostgreSQL data type representing a range of `timestamp with time zone` values |
| **TOCTOU** | Time-of-check to time-of-use: a race condition class where the state changes between validation and action |
| **MDC** | Mapped Diagnostic Context: per-thread key-value data (such as `traceId`) attached to every log line |
| **Modular Monolith** | An architectural pattern where the application is deployed as a single unit but internally organized into loosely coupled modules |