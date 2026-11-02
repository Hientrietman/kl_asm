# Requirements Review: Why Each Point Needed Improvement

This document compares your original `requirements.md` with the Keyloop Scenario A brief and with best practices for PostgreSQL, Spring Boot and REST APIs. The corresponding improved file is `requirements.md` (version 2.0).

---

## 1. Summary

Findings are grouped into five categories:

| Category | ID prefix | Meaning | Count |
|----------|-----------|---------|-------|
| Brief Mismatch | BM | Content that contradicts or does not follow the assessment brief | 6 |
| Ambiguity | AM | Not measurable, not testable, or open to different readings | 16 |
| Technical Inaccuracy | TI | A wrong statement, or two parts of the document that contradict each other | 8 |
| Missing Requirement | MR | Needed by the brief or for correctness, but not covered | 8 |
| Document Quality | DQ | Leftovers from another stack, typos, empty or unfinished sections | 6 |

The three most serious findings (read these first):

1. **A-2** forces the user to choose the service bay and technician, while the brief gives the system the job of finding and checking both resources.
2. **ADR-003** has no `WHERE status = 'CONFIRMED'` condition, so cancelled appointments still block slots. This directly contradicts A-9.
3. **ADR-003** describes a single `(resource_id, tstzrange)` constraint, but bay and technician are different columns and each needs its own constraint. The vehicle also had no constraint at all.

---

## 2. Brief Mismatch (BM)

### BM-1. A-2: the user selects the bay and technician

- **Original:** "Service bay and technician are explicitly selected ... The system does not auto-assign."
- **Problem:** The brief (CR1) only asks the user to provide a vehicle, service type, dealership and desired time. CR2 requires the system to "check for the availability of both a ServiceBay and a qualified Technician". Finding the bay and technician is therefore the system's responsibility. If the user must choose them, "resource constrained booking" shrinks to "conflict detection", and the user has to know which bay is free in advance, which is exactly the question the system is supposed to answer.
- **Weakness of the original rationale:** "Auto-assignment needs an optimization algorithm." In practice a deterministic heuristic is enough; no solver is required.
- **Improvement:** A-2, FR-2.4, FR-2.5 and ADR-009. By default the system assigns resources (lowest bay `code`, technician with the fewest booked minutes that day). `serviceBayId` and `technicianId` become optional overrides only.

### BM-2. FR-1 and FR-2 are internally inconsistent

- **Problem:** FR-1 lists the input as customer, VIN, service type, dealership and time (no bay or technician), yet FR-2 refers to "the selected service bay" and "the selected technician". The reader cannot tell where the bay and technician come from.
- **Improvement:** FR-1 now has a complete field table in which `serviceBayId` and `technicianId` are marked optional. FR-2 describes two modes (auto-assignment and explicit override).

### BM-3. NFR-3 covers logging only

- **Original:** "Structured JSON logging with correlation/trace IDs."
- **Problem:** The brief asks for a strategy covering "logging, metrics, tracing" and lists observability under "Build for the Future". Logging alone covers one third of that.
- **Improvement:** NFR-3 now covers logging (with `traceId` and `spanId`, no PII), metrics (Micrometer, `/actuator/prometheus`, custom counters and a timer), tracing (OpenTelemetry through Micrometer Tracing) and health probes (liveness and readiness). ADR-006 and the `observability` profile in ADR-007 support it.

### BM-4. The "Build for the Future" criteria are not mapped

- **Problem:** The brief lists five qualities: scalability, performance, reliability, maintainability and observability. The original only addressed performance (NFR-2) and observability (NFR-3). There was no requirement for scalability, reliability or maintainability.
- **Improvement:** Added NFR-8 (reliability), NFR-9 (scalability) and NFR-10 (maintainability), plus a traceability table at the top so reviewers can see where each brief item is covered.

### BM-5. "User" and "customer" are not distinguished

- **Problem:** CR1 says "a user" and CR3 says "customer". The original wrote "Customer information" without saying what it contains or how it relates to the user. The brief itself flags this as an ambiguity to document.
- **Improvement:** A-5 states that the "user" is whoever calls the API (not modeled, because there is no authentication) and that the customer is the person described in the request body (full name, email, phone). A-13 defines customer identity by email and vehicle identity by VIN.

### BM-6. FR-4 is labeled "Bonus" but under-specified

- **Problem:** The brief does not require FR-4, but once it is included it must be precise. An unpaginated list is also a performance and reliability risk, which conflicts with "Build for the Future".
- **Improvement:** Marked SHOULD, added pagination (`page`, `size` with a maximum of 100), a default sort order and a concrete cancel endpoint (FR-4).

---

## 3. Ambiguity (AM)

### Functional requirements

| ID | Original point | Problem | Improvement |
|----|----------------|---------|-------------|
| AM-1 | FR-1: VIN | Only "identified by VIN", with no constraint. | Exactly 17 characters, regex `^[A-HJ-NPR-Z0-9]{17}$` (excludes I, O, Q per ISO 3779), trimmed and upper-cased before validation, with a matching `CHECK` in the database. The check digit is not validated because it is mandated only for North American vehicles (49 CFR 565), and validating it would wrongly reject valid VINs (A-13). |
| AM-2 | FR-1: "Customer information" | No fields, lengths or formats. | `fullName` 1-100 characters, `email` valid, max 254 characters, normalized to lower case, `phone` in E.164 format (optional). |
| AM-3 | FR-1: "Desired date and time" | No format, time zone, granularity or limits. | ISO-8601 with offset, on a 15-minute grid, at least 60 minutes in the future, at most 90 days ahead, and inside the dealership's working hours in its own time zone (FR-1.3). |
| AM-4 | FR-1: customer or vehicle does not exist yet | Unclear whether to create or reject. If the VIN exists with different details, is it overwritten? | FR-1.2: find or create, never modify existing data from a booking request, all in the same transaction. |
| AM-5 | FR-1: `end_time` | No unit, no storage detail, no rules for duration. | `duration_minutes` is a multiple of 15, maximum 480, enforced by a database `CHECK` (A-4, A-12). |
| AM-6 | FR-2: what counts as a "conflict" | The range boundary is not defined. Do 9:00-10:00 and 10:00-11:00 conflict? | FR-2.1: half-open range `[start, end)`, so back-to-back appointments do not conflict. This also matches `tstzrange(..., '[)')` in the database. |
| AM-7 | FR-2: which statuses block resources | Only mentioned in A-9, not in FR-2. | FR-2.2: only `CONFIRMED` blocks. |
| AM-8 | FR-2: "409 with details" | "Details" is undefined, and other failures (not found, wrong dealership, not qualified) have no status code. | New "API Conventions and Error Contract" section: full error catalog, 400 for syntax errors, 422 for business rule violations, 409 for conflicts, RFC 9457 format. |
| AM-9 | FR-2: no qualified technician exists | Not addressed. | `TECHNICIAN_NOT_QUALIFIED` (422), distinct from `NO_AVAILABILITY` (409, qualified people exist but are all busy). |
| AM-10 | FR-3: "unique identifier" | No id type, status code or headers. | UUID, `201 Created` with a `Location` header, and a response body describing the assigned bay, technician and dealership time zone (FR-3.2). |
| AM-11 | FR-4: filtering by "date range" | Inclusive or exclusive bounds, and which field, are not stated. | `from` inclusive, `to` exclusive, applied to `start_time` (FR-4.2). |
| AM-12 | FR-4: cancel | No endpoint, no idempotency rule, no cancellation conditions. | `POST /appointments/{id}/cancel`; a second cancel returns `200` with the current state; an appointment that has already started cannot be cancelled (`422`). |

### Non-functional requirements

| ID | Original point | Problem | Improvement |
|----|----------------|---------|-------------|
| AM-13 | NFR-1 Consistency | The target is right but cannot be verified. | Added a Verification column: 20 parallel requests for the same slot must produce exactly one `201` and nineteen `409`. |
| AM-14 | NFR-2 "< 500ms" | No percentile, load or data size, so it cannot be measured. | p95 below 500 ms at 20 concurrent users with at least 10,000 seeded appointments, with a load-test script in the README. |
| AM-15 | NFR-4, NFR-5, NFR-6 | "Covered by tests", "auto-generated" and "no manual setup" are generic. | Concrete test layers (unit, Testcontainers, API), an 80% coverage target for the service layer, springdoc-openapi, a committed `openapi.yaml`, health checks and `depends_on: service_healthy` in Compose. |
| AM-16 | NFR-7 Data Integrity | Only names the kinds of constraints. | New "Domain Model and Data Constraints" section lists every table and column with CHECK, UNIQUE, FK and composite FK constraints. |

---

## 4. Technical Inaccuracy (TI)

### TI-1. ADR-003 lacks a partial predicate: cancelled appointments still block slots

- **Problem:** A-9 says cancelled appointments "no longer block availability" and that "the availability check filters by `status != 'CANCELLED'`", but the exclusion constraint in ADR-003 has no `WHERE` clause. The database constraint knows nothing about an application-level filter, so a cancelled appointment would still cause new inserts to fail with `23P01`. The application filter cannot rescue this because the database makes the final decision.
- **Improvement:** The constraint now includes `WHERE (status = 'CONFIRMED')`, and A-9 was rewritten to match.
- **Basis:** PostgreSQL documentation, Exclusion Constraints (predicates are supported).

### TI-2. ADR-003 describes one constraint for a generic "resource_id"

- **Problem:** Bay and technician are separate columns in the same table. A single `(resource_id, range)` constraint cannot cover both. Each resource needs its own constraint (`service_bay_id WITH =`, `technician_id WITH =`).
- **Improvement:** ADR-003 lists three constraints with full SQL and constraint names, so the application can map `23P01` to the correct `409` code using the constraint name.

### TI-3. No constraint for the vehicle

- **Problem:** A vehicle cannot be in two bays at the same time. The original only blocked double-booking of bays and technicians, so two overlapping appointments could be created for the same VIN.
- **Improvement:** FR-2.6, A-14, the third constraint in ADR-003 and the error code `VEHICLE_ALREADY_BOOKED`.

### TI-4. ADR-001: the MongoDB statement is outdated

- **Original:** "MongoDB: no ACID transactions across documents."
- **Problem:** MongoDB has supported multi-document ACID transactions since 4.0 (replica sets) and 4.2 (sharded clusters). The statement is no longer correct. The accurate reason not to choose MongoDB is that it has no range type or exclusion constraint, so preventing overlaps would still depend on application logic.
- **Improvement:** Rewrote the argument. Also tightened the SQLite argument: "not suitable for concurrent access" is too generic; the precise points are the lack of exclusion constraints and serialized writes. Added the "slot table" alternative (one row per resource per 15-minute slot with a UNIQUE key), which is a realistic option with a 15-minute grid.
- **As you asked:** this statement conflicts with the official MongoDB documentation, so I explained the correct version instead of keeping the original.

### TI-5. ADR-003: "application-level optimistic locking" is the wrong term

- **Problem:** Optimistic locking (a version column) protects updates to an existing row. It does not protect the insertion of a new overlapping row. What the original describes (check, then insert) is check-then-insert, not optimistic locking.
- **Improvement:** Renamed to "Check-then-insert in the application" and added an explanation of why optimistic locking does not apply.

### TI-6. ADR-003: the `SELECT FOR UPDATE` argument is imprecise

- **Problem:** The original says it "locks rows broadly". The real weakness is that `FOR UPDATE` cannot lock a row that does not exist yet (the appointment about to be inserted). To use it you must lock the parent resource rows (bay or technician), which serializes all bookings for that resource.
- **Improvement:** Rewrote the argument and added SERIALIZABLE isolation as another alternative.

### TI-7. ADR-003: "Mathematically guaranteed"

- **Problem:** Overstated wording. What is guaranteed is that the database engine enforces the constraint.
- **Improvement:** Replaced with precise wording in the Consequences row.

### TI-8. A-4 and A-7 lead to an awkward design

- **A-4 (1-minute granularity):** It is "more flexible" but creates fragmented gaps, makes availability impossible to list in a finite way, and allows start times such as `10:00:37`. The brief itself mentions slot granularity (for example 30 minutes) as a decision to make. Changed to a 15-minute grid (A-4, ADR-008).
- **A-7 (UTC only):** Storing UTC is correct, but "time zone conversion is the frontend's job" is not enough once you validate working hours, because working hours are local and affected by daylight saving time. UTC is kept for storage, and each dealership gets an IANA time zone (A-7, ADR-008).

---

## 5. Missing Requirement (MR)

| ID | Addition | Reason |
|----|----------|--------|
| MR-1 | A-3 changed to **enforce working hours** | Once the system assigns the bay and technician, without working hours it could confirm an appointment at 3 a.m. The original argued that operating hours add complexity, but one open interval per day is a small model (A-3, A-16, `dealership_working_hours` table). |
| MR-2 | **Idempotency-Key** (FR-3.4, ADR-010) | Clients retry on timeouts; without idempotency this creates duplicates or a misleading `409`. A unique constraint keeps it safe under concurrency. Reference: the IETF `Idempotency-Key` HTTP header draft. |
| MR-3 | **FR-5: availability query** | The brief says "Real-Time Availability Check". Users need to know which times are free before booking. It is explicitly informational, not a reservation. |
| MR-4 | **FR-6: read-only reference data** | With seeded data (A-6), a client or Swagger UI needs a way to discover valid ids; otherwise the API cannot be demonstrated. |
| MR-5 | **Domain model and data constraints** | Makes NFR-7 concrete. The composite FKs `(dealership_id, service_bay_id)` also guarantee at database level that a bay belongs to the right dealership. |
| MR-6 | **Error catalog** | A consistent error contract makes API tests and the demo video easier. |
| MR-7 | **Traceability** to the brief and the evaluation criteria | Lets reviewers see which requirement satisfies which part of the brief. |
| MR-8 | **New assumptions A-10 to A-17** | Cover ambiguities the brief itself admits: service duration, which dealership owns each resource, buffer time, customer identity, booking window, idempotency key retention. |

Also added to Out of Scope: holidays, breaks and technician shifts, buffer time, optimization solver, waiting list, and the AI assistant (optional, postponed). The CI/CD row was reworded neutrally because the pipeline could be GitHub Actions or AWS CodePipeline.

---

## 6. Document Quality (DQ)

| ID | Location | Problem | Improvement |
|----|----------|---------|-------------|
| DQ-1 | ADR-004 | Looks copied from NestJS: "Decorators for validation (`class-validator`)", "guards", "Module system maps naturally", "Decorator-heavy style". Spring Boot uses annotations and Jakarta Bean Validation; it has no `class-validator` or guards (the equivalent is a Spring Security filter). The Context sentence is also cut off, the Alternatives row is missing, and a table cell is missing its closing `|`. | Fully rewritten: Java 21, Spring Boot 3.4+, `@Valid` and custom constraints such as `@Vin`, `@Transactional`, springdoc-openapi, Actuator. The real trade-off of Spring is hidden auto-configuration and proxies (for example, self-invocation bypasses `@Transactional`). |
| DQ-2 | ADR-005 | Typo "Srping" (three times), an awkward trailing sentence, and "JPA with native query" is unclear. JPA is a specification; the actual implementation is Hibernate. | Names Spring Data JPA with Hibernate, native SQL for PostgreSQL features, and Flyway owning the schema (`ddl-auto=validate`, `open-in-view=false`). Added Alternatives (jOOQ, JdbcTemplate/MyBatis, Hibernate auto DDL). |
| DQ-3 | ADR-006 | Titled "Pino", a Node.js logging library that cannot be used in Spring Boot. Body was empty. | Uses Spring Boot 3.4+ native structured logging (`logging.structured.format.console=ecs`), with `traceId` and `spanId` entering the MDC from Micrometer Tracing, and a no-PII rule. |
| DQ-4 | ADR-007 | Empty. | Completed: `postgres:16-alpine` with a health check, multi-stage Dockerfile running as non-root, `depends_on` with `service_healthy`, `.env.example`, and an optional `observability` profile. |
| DQ-5 | ADR-003 | The Implementation row was a placeholder: "See [migration file]". | Names the migration `V2__add_exclusion_constraints.sql` and includes the SQL in the document. |
| DQ-6 | ADR-002 | Modules and boundaries were not defined ("clear module boundaries (appointments, availability, dealerships, etc.)"). | Defined the module list, the rule that modules call each other only through service interfaces, and an ArchUnit check. Added hexagonal architecture and Spring Modulith as alternatives for a clearer comparison. Each module uses a layered structure (controller, service, repository). |

Also, all icon symbols in the Consequences rows were replaced with the labels "Positive:" and "Negative:", as you requested.

---

## 7. New ADRs

The new requirements needed explicit architecture decisions, so five ADRs were added in the same format:

| ADR | Topic | Why it exists |
|-----|-------|---------------|
| ADR-008 | Time handling: UTC, dealership time zone, 15-minute grid | Explains A-4 and A-7 |
| ADR-009 | Auto-assignment | Explains the change to A-2 |
| ADR-010 | Idempotent booking | Explains FR-3.4 |
| ADR-011 | REST conventions and error format (RFC 9457) | Explains the error catalog |
| ADR-012 | Testing strategy (Testcontainers) | H2 does not support exclusion constraints, so NFR-1 cannot be verified with H2 |

---

## 8. Defaults I Chose (you can change them)

| Value | Default | Note |
|-------|---------|------|
| Minimum lead time | 60 minutes | `booking.min-lead-time` |
| Maximum booking horizon | 90 days | `booking.max-horizon` |
| Maximum service duration | 480 minutes | `CHECK` constraint |
| Retries on `23P01` during auto-assignment | 3 | Balance between load and success rate |
| `Idempotency-Key` | Optional | Easier to test with cURL; can be made required |
| Technician selection rule | Fewest booked minutes that day | Could be changed to round-robin |
| Cancel endpoint | `POST /appointments/{id}/cancel` | Could be `DELETE` for a more purist REST style |
| Log format | ECS | Can be switched to `logstash` with one configuration line |

---

## 9. References

- PostgreSQL documentation: Exclusion Constraints (`ddl-constraints`), Range Types (`rangetypes`, default `[)` convention), `btree_gist` extension (`btree-gist`), Appendix A Error Codes (`23P01 exclusion_violation`).
- RFC 9457: Problem Details for HTTP APIs.
- IETF HTTPAPI working group: The Idempotency-Key HTTP Header Field (draft).
- Spring Boot reference documentation: Logging, Structured Logging section (since 3.4); Actuator; Micrometer Tracing.
- Spring Framework reference: Error Responses (`ProblemDetail`), Transaction Management (proxy limitations).
- Flyway documentation: Versioned Migrations.
- MongoDB manual: Transactions.
- ISO 3779 (VIN structure) and 49 CFR Part 565 (check digit for North American vehicles).