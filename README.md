# Unified Service Scheduler (Keyloop Scenario A)

A high-concurrency, resource-constrained vehicle service scheduling platform built with Java 17 and Spring Boot 3.5.11.

The system replaces manual booking workflows by enabling vehicle owners and dealerships to schedule service appointments with real-time, race-free resource allocation across service bays and certified technicians.

---

## 1. System Architecture & Key Capabilities

- **Modular Monolith**: Package-by-feature architecture (`appointment`, `availability`, `dealership`, `customer`, `catalog`) communicating strictly through public interfaces (ADR-002).
- **Zero Double-Booking Guarantee (ADR-003)**: Non-overlapping capacity enforced at the database level by PostgreSQL GiST exclusion constraints (`btree_gist`) over half-open intervals `[start, end)`.
- **Deadlock-Free High Concurrency (ADR-009, M-003)**: Concurrent auto-assignment requests serialize resource allocation using transaction-scoped PostgreSQL advisory locks acquired in deterministic sorted key order before insert, backed by bounded retries on exclusion violation (`23P01`).
- **Idempotency (ADR-010, FR-3.4)**: Safe client retries supported via optional `Idempotency-Key` header with SHA-256 payload normalization. Duplicate requests replay the original response (`200 OK`), while key collisions with divergent payloads reject with `422 IDEMPOTENCY_KEY_MISMATCH`.
- **Operating Hours & 15-Minute Grid (FR-1, FR-5)**: Start times and durations align to 15-minute intervals. Operating hours are evaluated per dealership in their local IANA time zone (`Europe/London`, `Asia/Ho_Chi_Minh`), while all timestamps persist in UTC (`timestamptz`).
- **Observability & Privacy (NFR-3, ADR-011)**: Structured JSON logging (ECS format) with MDC `traceId` and `spanId`. Full PII protection: customer names, emails, and phone numbers are excluded from operational logs, and VINs are truncated to their last 6 characters.

---

## 2. Technology Stack

- **Runtime**: Java 17 (Eclipse Temurin)
- **Framework**: Spring Boot 3.5.11 (Web MVC, Data JPA, Validation, Actuator)
- **Database**: PostgreSQL 16 with `btree_gist` extension
- **Database Migrations**: Flyway
- **API Documentation**: OpenAPI 3.1 via SpringDoc OpenAPI (`/v3/api-docs`, `/swagger-ui.html`, and root `openapi.yaml`)
- **Observability**: Micrometer with Prometheus registry, Micrometer Tracing with OpenTelemetry bridge, ECS logback encoder
- **Testing**: JUnit 5, Mockito, REST Assured, Testcontainers PostgreSQL, ArchUnit, JaCoCo, Spotless (palantir-java-format)

---

## 3. Getting Started

### Prerequisites

- Java 17 JDK installed
- Docker & Docker Compose running

### Option A: Run Full Application in Docker Compose

Start both PostgreSQL and the application container:

```bash
docker compose up --build
```

The application starts after the database is healthy, automatically applies Flyway migrations (`V1`, `V2`, `V3`), and listens on port `8080`.

- API Base URL: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health Probe: `http://localhost:8080/actuator/health/readiness`
- Prometheus Metrics: `http://localhost:8080/actuator/prometheus`

To stop:
```bash
docker compose down
```

### Option B: Run Database in Docker & Application Locally

1. Start the PostgreSQL container:
   ```bash
   docker compose up -d db
   ```

2. Run the application via the Maven wrapper:
   ```bash
   # Linux / macOS
   ./mvnw spring-boot:run

   # Windows PowerShell
   .\mvnw.cmd spring-boot:run
   ```

### 3.3 Quick 3-Minute Verification Walkthrough

Once running, execute this step-by-step sequence in your terminal to verify the entire system end-to-end:

```bash
# 1. Health check & readiness
curl -s http://localhost:8080/actuator/health/readiness

# 2. Query available slots for London (Oil Change, Mon 2026-11-16)
curl -s "http://localhost:8080/api/v1/dealerships/d0000000-0000-0000-0000-000000000001/availability?serviceTypeId=c0000000-0000-0000-0000-000000000001&date=2026-11-16"

# 3. Create appointment with auto-assignment & Idempotency-Key (Returns 201 Created)
curl -i -X POST http://localhost:8080/api/v1/appointments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-001" \
  -d '{
    "dealershipId": "d0000000-0000-0000-0000-000000000001",
    "serviceTypeId": "c0000000-0000-0000-0000-000000000001",
    "startTime": "2026-11-16T09:00:00Z",
    "customer": { "fullName": "Jane Doe", "email": "jane@example.com", "phone": "+447911123456" },
    "vehicle": { "vin": "1HGCM82633A004352", "make": "Honda", "model": "Accord", "year": 2022 }
  }'

# 4. Replay exact same request with same Idempotency-Key (Returns 200 OK with original appointment)
curl -i -X POST http://localhost:8080/api/v1/appointments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-001" \
  -d '{
    "dealershipId": "d0000000-0000-0000-0000-000000000001",
    "serviceTypeId": "c0000000-0000-0000-0000-000000000001",
    "startTime": "2026-11-16T09:00:00Z",
    "customer": { "fullName": "Jane Doe", "email": "jane@example.com", "phone": "+447911123456" },
    "vehicle": { "vin": "1HGCM82633A004352", "make": "Honda", "model": "Accord", "year": 2022 }
  }'

# 5. Idempotency Key Mismatch (same key, different vehicle VIN -> Returns 422 IDEMPOTENCY_KEY_MISMATCH)
curl -i -X POST http://localhost:8080/api/v1/appointments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: demo-key-001" \
  -d '{
    "dealershipId": "d0000000-0000-0000-0000-000000000001",
    "serviceTypeId": "c0000000-0000-0000-0000-000000000001",
    "startTime": "2026-11-16T09:00:00Z",
    "customer": { "fullName": "Jane Doe", "email": "jane@example.com", "phone": "+447911123456" },
    "vehicle": { "vin": "1HGCM82633A004353", "make": "Honda", "model": "Accord", "year": 2022 }
  }'

# 6. Verify PII-free logs (shows masked vinSuffix, no email/phone)
docker compose logs app | grep "Booking"

# 7. Scrape custom business metrics
curl -s http://localhost:8080/actuator/prometheus | grep appointments_booked_total
```

---

## 4. API Reference & Sample cURL Requests

The following examples use pre-seeded reference data from migration `V3__seed_reference_data.sql`:
- **Dealership London**: `d0000000-0000-0000-0000-000000000001` (Timezone: `Europe/London`, Mon–Fri 08:00–17:00, Sat 08:00–12:00)
- **Dealership Ho Chi Minh**: `d0000000-0000-0000-0000-000000000002` (Timezone: `Asia/Ho_Chi_Minh`)
- **Service Types**:
  - `c0000000-0000-0000-0000-000000000001` — `OIL_CHANGE` (60 min)
  - `c0000000-0000-0000-0000-000000000002` — `TIRE_ROTATION` (30 min)
  - `c0000000-0000-0000-0000-000000000003` — `BRAKE_SERVICE` (120 min)
  - `c0000000-0000-0000-0000-000000000004` — `FULL_INSPECTION` (180 min)

### 4.1 Reference Data Lookups (FR-6)

**List Dealerships with Operating Hours:**
```bash
curl -i http://localhost:8080/api/v1/dealerships
```

**List Service Bays for a Dealership:**
```bash
curl -i http://localhost:8080/api/v1/dealerships/d0000000-0000-0000-0000-000000000001/service-bays
```

**List Technicians and Qualifications for a Dealership:**
```bash
curl -i http://localhost:8080/api/v1/dealerships/d0000000-0000-0000-0000-000000000001/technicians
```

**List Service Types:**
```bash
curl -i http://localhost:8080/api/v1/service-types
```

---

### 4.2 Query Availability (FR-5)

Find free appointment start times for a given service type on a local dealership date:

```bash
curl -i "http://localhost:8080/api/v1/dealerships/d0000000-0000-0000-0000-000000000001/availability?serviceTypeId=c0000000-0000-0000-0000-000000000001&date=2026-11-16"
```

**Response (200 OK):**
```json
{
  "dealershipId": "d0000000-0000-0000-0000-000000000001",
  "serviceTypeId": "c0000000-0000-0000-0000-000000000001",
  "date": "2026-11-16",
  "availableSlots": [
    {
      "startTime": "2026-11-16T08:00:00Z",
      "localStartTime": "2026-11-16T08:00:00+00:00"
    },
    {
      "startTime": "2026-11-16T08:15:00Z",
      "localStartTime": "2026-11-16T08:15:00+00:00"
    }
  ]
}
```

---

### 4.3 Book an Appointment with Auto-Assignment (FR-2, FR-3)

Create an appointment. The system automatically assigns an eligible bay and qualified technician:

```bash
curl -i -X POST http://localhost:8080/api/v1/appointments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-booking-key-001" \
  -d '{
    "dealershipId": "d0000000-0000-0000-0000-000000000001",
    "serviceTypeId": "c0000000-0000-0000-0000-000000000001",
    "startTime": "2026-11-16T09:00:00Z",
    "customer": {
      "fullName": "Jane Doe",
      "email": "jane.doe@example.com",
      "phone": "+447911123456"
    },
    "vehicle": {
      "vin": "1HGCM82633A004352",
      "make": "Honda",
      "model": "Accord",
      "year": 2022
    },
    "notes": "Regular scheduled oil maintenance"
  }'
```

**Response (201 Created):**
```json
{
  "id": "e8a93945-87ab-4951-9ef2-5b9dbf8aa041",
  "dealershipId": "d0000000-0000-0000-0000-000000000001",
  "serviceTypeId": "c0000000-0000-0000-0000-000000000001",
  "serviceBayId": "b1000000-0000-0000-0000-000000000001",
  "serviceBayCode": "BAY-01",
  "technicianId": "a1000000-0000-0000-0000-000000000001",
  "technicianName": "Alice Morgan",
  "startTime": "2026-11-16T09:00:00Z",
  "endTime": "2026-11-16T10:00:00Z",
  "status": "CONFIRMED",
  "notes": "Regular scheduled oil maintenance"
}
```

*Note: Resending the exact same request with the same `Idempotency-Key` returns `200 OK` with the existing appointment.*

---

### 4.4 Get Appointment by ID (FR-4.1)

```bash
curl -i http://localhost:8080/api/v1/appointments/e8a93945-87ab-4951-9ef2-5b9dbf8aa041
```

---

### 4.5 List & Filter Appointments (FR-4.2)

Filter appointments by dealership, status, time range, and VIN with pagination:

```bash
curl -i "http://localhost:8080/api/v1/appointments?dealershipId=d0000000-0000-0000-0000-000000000001&status=CONFIRMED&page=0&size=10"
```

---

### 4.6 Cancel an Appointment (FR-4.3)

Cancelling is an idempotent soft-delete that immediately releases the bay, technician, and vehicle:

```bash
curl -i -X POST http://localhost:8080/api/v1/appointments/e8a93945-87ab-4951-9ef2-5b9dbf8aa041/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "Customer rescheduled trip"
  }'
```

**Response (200 OK):**
```json
{
  "id": "e8a93945-87ab-4951-9ef2-5b9dbf8aa041",
  "status": "CANCELLED",
  "cancelledAt": "2026-10-30T14:40:00Z",
  "cancellationReason": "Customer rescheduled trip"
}
```

---

## 5. RFC 9457 Error Contract

All error responses adhere to RFC 9457 Problem Details (`application/problem+json`) with an `X-Trace-Id` correlation header:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "No available bay and technician for this time slot",
  "code": "NO_AVAILABILITY",
  "traceId": "d50a42055b4f591df3dd2e0fffe63139",
  "conflict": {
    "alternatives": [
      "2026-11-16T10:00:00Z",
      "2026-11-16T10:15:00Z",
      "2026-11-16T10:30:00Z"
    ]
  }
}
```

### Error Catalog

| HTTP | `code` | Scenario |
|---|---|---|
| `400` | `VALIDATION_ERROR` | Malformed JSON, invalid VIN, off 15-minute grid, missing required fields |
| `404` | `NOT_FOUND` | Resource (dealership, service type, bay, technician, or appointment) does not exist |
| `409` | `RESOURCE_CONFLICT` | Explicitly requested bay or technician is already booked |
| `409` | `NO_AVAILABILITY` | Auto-assignment found no free bay and qualified technician pair (includes alternative start suggestions) |
| `409` | `VEHICLE_ALREADY_BOOKED` | Vehicle already has an overlapping confirmed appointment |
| `422` | `OUTSIDE_WORKING_HOURS` | Appointment range falls outside dealership operating hours or on a closed day |
| `422` | `INVALID_BOOKING_WINDOW` | Start time violates lead time (< 60 min) or horizon (> 90 days) policy |
| `422` | `TECHNICIAN_NOT_QUALIFIED` | Explicitly selected technician lacks required service qualification |
| `422` | `RESOURCE_DEALERSHIP_MISMATCH`| Selected bay or technician belongs to a different dealership |
| `422` | `IDEMPOTENCY_KEY_MISMATCH` | Idempotency key reused with a different request payload |
| `422` | `APPOINTMENT_NOT_CANCELLABLE` | Cannot cancel an appointment that has already started or elapsed |
| `500` | `INTERNAL_ERROR` | Unexpected server error (internal details sanitized) |

---

## 6. Observability

### Structured JSON Logging (ECS Format)

In the `docker` profile, logs are formatted as ECS-compliant JSON with MDC trace/span correlation and strict PII protection:

```json
{"@timestamp":"2026-10-30T14:44:25.449Z","log.level":"INFO","message":"Booking CREATED appointmentId=0f8ea8ed-8829-4291-996e-6b432f91d7f8 dealershipId=d0000000-0000-0000-0000-000000000001 serviceTypeId=c0000000-0000-0000-0000-000000000001 serviceBayId=b1000000-0000-0000-0000-000000000001 technicianId=a1000000-0000-0000-0000-000000000001 vinSuffix=004352","service.name":"klasm","trace.id":"3fa5c13465a24f898eae4010c7d1c389","transaction.id":"709ed1ad62fb4642"}
```

*Customer names, emails, phones, and cancellation reasons are never logged. VINs are logged with the last 6 characters only.*

### Prometheus Metrics

Scrape custom business and latency metrics:

```bash
curl http://localhost:8080/actuator/prometheus
```

Custom metric indicators:
- `appointments_booked_total`: Total successful appointment bookings created
- `booking_conflicts_total{reason="resource|no_availability|vehicle"}`: Booking conflicts categorized by cause
- `booking_assignment_retries_total`: Retries executed during concurrent auto-assignment races
- `booking_duration_seconds`: Histogram and summary of end-to-end booking execution latency

---

## 7. Testing & Verification

### Running the Test Suite

- **Unit tests only (no Docker required)**:
  ```bash
  ./mvnw test
  ```
- **Full verification (unit tests, ArchUnit, Testcontainers PostgreSQL integration tests)**:
  ```bash
  ./mvnw clean verify
  ```
- **Code formatting check**:
  ```bash
  ./mvnw spotless:check
  ```
- **Code formatting apply**:
  ```bash
  ./mvnw spotless:apply
  ```

### Code Coverage Report

After running `./mvnw clean verify`, open the JaCoCo coverage report in your browser:
```
target/site/jacoco/index.html
```

### High-Concurrency Invariant Test

`BookingConcurrencyIT` fires 20 parallel HTTP requests for the exact same slot and dealership. It asserts:
- Exactly **one** `201 Created`
- Exactly **nineteen** `409 Conflict` (catalog code `NO_AVAILABILITY`)
- Zero `500 Internal Error` responses

### Load & Performance Testing (NFR-2)

The repository includes a dedicated k6 performance testing suite with a seed script creating 10,800+ confirmed historical appointments across 1,000 customers and vehicles:

See the [load-test/README.md](load-test/README.md) for full instructions:
```bash
# 1. Seed load data
docker compose exec -T db psql -U klasm -d klasm < load-test/seed-load-data.sql

# 2. Execute k6 load benchmark (20 virtual users)
k6 run load-test/booking-load.js
```

---

## 8. AI Collaboration Narrative

### 8.1 Strategy for Guiding the AI

The development of the Unified Service Scheduler was conducted as a disciplined human-AI pair programming collaboration across Google Antigravity and GitHub Copilot. 

To maintain strict alignment across multiple AI sessions without context drift:
1. **Contract as Single Source of Truth**: `AGENTS.md` served as the immutable operational contract governing architecture constraints, technology choices, invariants, and delivery criteria.
2. **Contract-First & Test-Driven**: Every requirement slice followed a strict lifecycle: define requirements and error codes in documentation -> write unit tests -> implement business logic -> write Testcontainers integration tests -> verify with clean build before committing.
3. **Session Handoffs**: Detailed handoff documents (`docs/handoff/`) and a persistent collaboration log (`docs/ai-log.md`) preserved decisions, rationale, and state transitions between tool switches.

### 8.2 Process for Verifying and Refining AI Output

AI code generation was never accepted on trust; every architectural proposal and implementation was validated against real system behaviors:

1. **Catching GiST Exclusion Deadlocks under Concurrency (M-003)**:
   - *Problem*: The initial design assumed losing concurrent inserts on PostgreSQL GiST exclusion constraints would fail with SQLSTATE `23P01` (exclusion violation) and trigger an application retry. During the 20-request parallel test (`BookingConcurrencyIT`), exactly one request succeeded, but the remaining 19 failed with `500 Internal Server Error` due to PostgreSQL `40P01 (deadlock detected)`.
   - *Root Cause Analysis*: PostgreSQL GiST exclusion indexes insert the index tuple first and check for conflicts second. In-progress concurrent transactions inspecting each other's index entries wait on each other, causing the database deadlock detector to abort transactions after `deadlock_timeout`.
   - *Resolution*: Rather than weakening the constraint or catching generic database errors, we introduced transaction-scoped advisory locks acquired in sorted key order (`dealership:bay:technician:vin`) before insert. This serialized conflicting attempts while keeping the exclusion constraints as the ultimate authority.
2. **Framework 400 Problem Details Enrichment (M-004)**:
   - *Problem*: When testing endpoint query parameter validation (`AvailabilityIT`), Spring MVC returned standard 400 responses without the expected catalog `code` or `traceId`.
   - *Resolution*: Investigated `ResponseEntityExceptionHandler` behavior and discovered it passes `null` bodies for framework validation exceptions. Refined `GlobalExceptionHandler` to update and resolve bodies before enrichment.
3. **Formatting & Architectural Boundaries**:
   - Spotless (Palantir Java format) was integrated into the build pipeline to eliminate style drift.
   - ArchUnit tests enforced package-by-feature modular isolation (controllers cannot access repositories; modules communicate only via service interfaces; no field injection; no un-mocked clock calls).

### 8.3 Lessons Learned

- **Database-Level Invariants are Invaluable**: Application-level checks are susceptible to TOCTOU races. The combination of PostgreSQL GiST exclusion constraints and deterministic advisory locking guarantees data integrity under arbitrary concurrency.
- **Fail Fast, Log Mistakes**: Establishing `docs/mistakes.md` enabled systematic root-cause tracking and active prevention rules, preventing recurring errors across AI iterations.
