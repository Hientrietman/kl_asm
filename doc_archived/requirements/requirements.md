# Requirements & Architecture Decisions

## Scenario A: The Unified Service Scheduler

> An Appointment Scheduler application to replace manual booking systems for automotive dealerships.

---

## Table of Contents

- [Functional Requirements](#functional-requirements)
- [Non-Functional Requirements](#non-functional-requirements)
- [Assumptions](#assumptions)
- [Out of Scope](#out-of-scope)
- [Architecture Decision Records (ADRs)](#architecture-decision-records-adrs)
- [Glossary](#glossary)

---

## Functional Requirements

### FR-1: Resource Constrained Booking
- A user can request a service appointment specifying:
  - Customer information
  - Vehicle (identified by VIN)
  - Service type (e.g., Oil Change, Brake Inspection)
  - Preferred dealership
  - Desired date and time
- The system calculates the `end_time` based on `start_time + service_type.duration_minutes`.

### FR-2: Real-Time Availability Check
- Before confirming a booking, the system must verify:
  - **Service Bay availability**: No other appointment occupies the selected service bay during the requested time range.
  - **Technician availability**: No other appointment is assigned to the selected technician during the requested time range.
  - **Technician qualification**: The technician must be qualified to perform the requested service type.
- If a conflict exists, the system returns a `409 Conflict` with details about the conflict.

### FR-3: Confirmed Appointment Record
- Upon successful validation, the system persists an `Appointment` record containing:
  - Customer, Vehicle, Technician, Service Bay, Service Type, Dealership references
  - Start time, End time (calculated)
  - Status (`CONFIRMED`)
  - Created/Updated timestamps
- The appointment is returned with a unique identifier.

### FR-4: Appointment Management (Bonus)
- A user can retrieve an appointment by ID.
- A user can list appointments with optional filters (by dealership, date range, status).
- A user can cancel an appointment (soft delete via status change to `CANCELLED`).

---

## Non-Functional Requirements

| ID | Requirement | Target |
|----|-------------|--------|
| NFR-1 | **Consistency** | No double-booking is possible, even under concurrent requests |
| NFR-2 | **Response Time** | API responses < 500ms for single appointment operations |
| NFR-3 | **Observability** | Structured JSON logging with correlation/trace IDs |
| NFR-4 | **Testability** | Core business logic covered by unit and integration tests |
| NFR-5 | **API Documentation** | OpenAPI/Swagger spec auto-generated from code |
| NFR-6 | **Containerization** | Application runs via `docker-compose up` with no manual setup |
| NFR-7 | **Data Integrity** | Foreign key constraints, NOT NULL constraints, database-level validation |

---

## Assumptions

> These assumptions were made where the requirements were ambiguous. Each is documented here for transparency.

| ID | Assumption | Rationale |
|----|-----------|-----------|
| A-1 | **Single dealership context per request** — Each API request operates within the context of a single dealership. | The requirements mention "a specific dealership" per booking. Multi-dealership search is out of scope. |
| A-2 | **Service bay and technician are explicitly selected** — The user (or a simple frontend) selects both the service bay and technician. The system does not auto-assign. | Auto-assignment adds significant complexity (optimization algorithm). Explicit selection keeps the scope manageable while still demonstrating availability checking. |
| A-3 | **Operating hours are not enforced** — Appointments can be booked at any time. | Operating hours vary by dealership and add scheduling complexity. The core constraint (overlap prevention) is the focus. |
| A-4 | **Appointments are in 1-minute granularity** — The system uses `timestamptz` with no rounding to fixed slots. | Continuous time ranges are more flexible than fixed time slots and better demonstrate range-based constraint checking. |
| A-5 | **No authentication/authorization** — The API is open. In production, JWT/OAuth2 would be implemented. | Auth is orthogonal to the scheduling domain logic and would distract from the core assessment. |
| A-6 | **Technician qualifications are pre-seeded** — The many-to-many relationship between technicians and service types is populated via seed data. | A full CRUD for managing qualifications is not part of the core requirements. |
| A-7 | **All times are stored in UTC** — The API accepts and returns UTC timestamps. Client-side timezone conversion is the frontend's responsibility. | UTC storage is an industry best practice for scheduling systems to avoid timezone ambiguity. |
| A-8 | **No recurring appointments** — Each appointment is a one-time event. | Recurring scheduling (e.g., "every 6 months") is a significant feature that goes beyond the stated requirements. |
| A-9 | **Cancellation is a soft delete** — Cancelled appointments remain in the database with status `CANCELLED` and no longer block availability. | Soft delete preserves audit history. The availability check filters by `status != 'CANCELLED'`. |

---

## Out of Scope

The following features are **not implemented** but are acknowledged as natural extensions:

| Feature | Why Out of Scope |
|---------|-----------------|
| **User authentication & authorization** | Orthogonal to scheduling logic; would use standard JWT/OAuth2 in production |
| **Auto-assignment of technician/bay** | Requires optimization algorithm; explicit selection demonstrates the same constraints |
| **Notification system** (email/SMS) | Would be an event-driven extension; noted in design as a future domain event consumer |
| **Recurring appointments** | Complex scheduling logic beyond core requirements |
| **Multi-timezone per dealership** | Simplification: all times in UTC; production would store dealership timezone |
| **Appointment modification** (reschedule) | Equivalent to cancel + rebook; cancel is implemented |
| **Frontend UI** | Backend-focused implementation; API contract and Swagger UI serve as the client interface |
| **Rate limiting** | Production concern; would use API gateway or middleware |
| **Caching** | Not needed at assessment scale; would use Redis for availability lookups in production |
| **CI/CD pipeline** | Would use GitHub Actions; Dockerfile provided for containerization |
| **Payment/pricing** | Business domain extension, not part of scheduling core |

---

## Architecture Decision Records (ADRs)

### ADR-001: PostgreSQL as the Primary Database

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | We need a database that supports complex scheduling constraints, specifically preventing time range overlaps at the data level. |
| **Decision** | Use PostgreSQL with the `btree_gist` extension for exclusion constraints on time ranges. |
| **Alternatives Considered** | **MySQL** — lacks native exclusion constraints; overlap prevention would require application-level locking (error-prone). **MongoDB** — no ACID transactions across documents; scheduling requires strong consistency. **SQLite** — no exclusion constraints; not suitable for concurrent access patterns. |
| **Consequences** | ✅ Database-level guarantee against double-booking (race-condition proof). ✅ `tstzrange` type provides first-class time range support. ✅ Mature, well-documented, widely adopted. ⚠️ Requires `btree_gist` extension (included in standard PostgreSQL distributions). |

---

### ADR-002: Modular Monolith Architecture

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | We need an architecture that balances clean code organization with development simplicity for a single-team, single-domain application. |
| **Decision** | Structure the application as a modular monolith with clear module boundaries (appointments, availability, dealerships, etc.), but deploy as a single process. |
| **Alternatives Considered** | **Microservices** — introduces network overhead, distributed transaction complexity, and operational burden (service discovery, circuit breakers) that are unjustified for this scope. **Traditional monolith (no module boundaries)** — leads to spaghetti code and makes future extraction difficult. |
| **Consequences** | ✅ Simple deployment (single container). ✅ No network latency between modules. ✅ Clear module boundaries make future microservice extraction straightforward. ✅ Single database transaction for booking (no saga/2PC needed). ⚠️ All modules share the same process — a bug in one could affect others (acceptable at this scale). |

---

### ADR-003: Exclusion Constraints for Overlap Prevention

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | The core business rule is that a service bay and a technician cannot be double-booked. Two concurrent requests for the same slot must result in exactly one success. |
| **Decision** | Use PostgreSQL exclusion constraints (`EXCLUDE USING gist`) on `(resource_id, tstzrange(start_time, end_time))` to enforce non-overlapping appointments at the database level. |
| **Alternatives Considered** | **Application-level optimistic locking** — check-then-insert has a TOCTOU race condition window. **Pessimistic locking (SELECT FOR UPDATE)** — works but locks rows broadly and reduces throughput. **Advisory locks** — application must manage lock lifecycle; error-prone. |
| **Consequences** | ✅ Mathematically guaranteed: no overlapping ranges can exist for the same resource. ✅ Works correctly under concurrent transactions without application-level coordination. ✅ The database is the single source of truth for constraints. ⚠️ Requires `btree_gist` extension. ⚠️ Constraint violation produces a database error that must be caught and translated to a meaningful API response (409 Conflict). |
| **Implementation** | See [migration file] for the exact constraint definitions. |

---

### ADR-004: Spring Boot as the Application Framework

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | We need a Java framework that supports modular architecture, dependency injection
| **Decision** | Use Spring Boot with Java. |
| **Consequences** | ✅ Built-in dependency injection → testable, mockable services. ✅ Module system maps naturally to our modular monolith. ✅ Decorators for validation (`class-validator`), Swagger generation, and guards. ✅ Large ecosystem and community. ⚠️ Decorator-heavy style has a learning curve. |

---

### ADR-005: JPA for Database Access

| Aspect | Detail |
|--------|--------|
| **Status** | Accepted |
| **Context** | We need an ORM that integrates well with Spring Boot, supports PostgreSQL-specific features (like `tstzrange`), and provides migration tooling. |
| **Decision** | Use JPA with native query and Flyway for DB migration with its Srping Boot integration|
| **Consequences** | ✅ First-class Srping Boot integration. ✅ Migration system for schema versioning. ✅ Supports raw queries for PostgreSQL-specific features (exclusion constraints). raw SQL used where needed. |

---

### ADR-006: Structured JSON Logging with Pino


---

### ADR-007: Docker Compose for Local Development


---

## Glossary

| Term | Definition |
|------|-----------|
| **Appointment** | A confirmed booking of a service bay and technician for a specific vehicle at a specific time |
| **Service Bay** | A physical workspace in a dealership where vehicle servicing occurs |
| **Technician** | A dealership employee qualified to perform specific service types |
| **Service Type** | A category of vehicle service (e.g., Oil Change, Brake Inspection) with a defined duration |
| **VIN** | Vehicle Identification Number — a unique 17-character identifier for a vehicle |
| **Exclusion Constraint** | A PostgreSQL constraint that prevents overlapping values in specified columns |
| **tstzrange** | PostgreSQL data type representing a range of `timestamp with time zone` values |
| **TOCTOU** | Time-of-check to time-of-use — a race condition class where the state changes between validation and action |
| **Modular Monolith** | An architectural pattern where the application is deployed as a single unit but internally organized into loosely coupled modules |
