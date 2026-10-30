# AGENTS.md

Instructions for AI coding assistants working on the Unified Service Scheduler (Keyloop Scenario A).
Keep this file short and current. If a rule here conflicts with a request, say so before acting.  
This Project purpose's is to implement th requirements in a coding assessment.
## 1. Source of truth

- `docs/assements/keyloop_assessment.md`: Brief summary of the coding assessment.
- `docs/requirements/requirements.md`: requirements (FR, NFR), assumptions (A-x), error catalog, ADRs. This is the contract.
- `docs/designs/system_design_documents.md`: architecture, data flow, data model.
- If code and these documents disagree, or a requirement is ambiguous, stop and ask. Do not invent requirements and do not silently change the documents.

## 2. Fixed technology stack

Java 17, Spring Boot 3.5.11 or later, Spring Web MVC, Jakarta Bean Validation, Spring Data JPA (Hibernate) with native SQL where noted, Flyway, PostgreSQL 16 with `btree_gist`, springdoc-openapi, Spring Boot Actuator, Spring Boot structured logging (ECS) on Logback, Micrometer with the Prometheus registry, Micrometer Tracing with the OpenTelemetry bridge, JUnit 5, Mockito, MockMvc or REST Assured, ArchUnit, Testcontainers, JaCoCo.

- Do not add or upgrade dependencies without asking first.
- Build tool: Maven, always through the wrapper (`./mvnw`). Docker must be running for integration tests (Testcontainers).
    - Unit tests only: `./mvnw test`
    - Full build with unit and integration tests (`*IT` classes run by Failsafe): `./mvnw clean verify`
    - Coverage report: `target/site/jacoco/index.html` after `./mvnw verify`
    - Format: `./mvnw spotless:apply` (add Spotless to the build before relying on it)
    - Run locally: `docker compose up -d db`, then `./mvnw spring-boot:run`
    - Run everything in containers: `docker compose up --build`
    - If a command here stops working, fix the command in this file as part of the change.

## 3. Architecture rules

- Modular monolith, package-by-feature: `appointment`, `availability`, `dealership`, `customer`, `catalog`. Inside a module: controller, service, repository.
- A module calls another module only through its public service interface, never through its repositories or entities.
- Controllers contain no business logic. Do not expose JPA entities in the API; use separate request and response DTOs.
- Constructor injection only. No field injection.
- Put booking logic in a dedicated service bean. Calling a `@Transactional` method from the same class bypasses the proxy.

## 4. Non-negotiable invariants

These protect the core business rule (no double-booking). Never weaken them to make a test pass.

- Non-overlap is enforced by the three PostgreSQL exclusion constraints (bay, technician, vehicle) with `WHERE (status = 'CONFIRMED')` and ranges `tstzrange(start_time, end_time, '[)')`. An application-level check is only an optimization and never replaces them.
- Time ranges are half-open `[start, end)`.
- Store instants as `timestamptz` (UTC). Use `Instant` / `OffsetDateTime` for instants. Use `ZonedDateTime` with the dealership time zone only for working-hour and local-day logic. Never use `LocalDateTime` for an instant.
- Inject a `Clock`. Never call `Instant.now()` or `LocalDate.now()` directly in business code.
- The schema changes only through new Flyway migrations. Never edit a migration that has been applied or committed. Keep `spring.jpa.hibernate.ddl-auto=validate` and `spring.jpa.open-in-view=false`.
- Booking never modifies an existing customer or vehicle record (A-13).

## 5. Booking transaction rules

- A failed `INSERT` aborts the PostgreSQL transaction. Each insert attempt during auto-assignment must run in its own short transaction (or a savepoint) so a retry is possible.
- Retry only on SQLSTATE `23P01`, at most 3 attempts, moving to the next candidate. Use the constraint name to decide which error code to return. Do not catch generic exceptions to hide failures.
- Customer and vehicle resolution must be idempotent so repeating it is safe.
- Idempotency: same `Idempotency-Key` and same body returns the original result; same key and different body returns `IDEMPOTENCY_KEY_MISMATCH`.

## 6. API conventions

- Base path `/api/v1`. JSON only, camelCase fields, ISO-8601 timestamps with offset.
- Errors are RFC 9457 `application/problem+json` with a stable `code` from the error catalog in `docs/requirements/requirements.md`. 400 for malformed input, 422 for business rule violations, 409 for conflicts, 404 for missing resources.
- Never return stack traces, SQL or internal class names in responses.
- Every response carries `X-Trace-Id`.

## 7. Logging and observability

- Structured JSON logs with `traceId` and `spanId` (MDC). Use SLF4J with parameterized messages.
- Never log PII: email, phone, notes. Log the VIN only truncated to its last 6 characters.
- Metrics use the names in `docs/designs/system_design_documents.md` section 8.2.

## 8. Testing rules

- Test against a real PostgreSQL with Testcontainers. Do not use H2: it cannot verify exclusion constraints.
- Every business rule and every error code in the catalog needs at least one test.
- Keep the concurrency test (20 parallel requests for the same slot: exactly one `201`, nineteen `409`) passing at all times.
- Unit-test pure logic (assignment ordering, working-hour and grid validation, VIN validator) without Spring.
- Never delete, skip or weaken a failing test to get a green build. Fix the code or ask.

## 9. How to work

- For any non-trivial task, state a short plan first (files to change, approach, risks) and wait for confirmation.
- Work in small steps; one concern per change. Prefer the simplest solution that satisfies the requirement.
- Run the build and the full test suite before saying a task is done, and report the actual result.
- If you are unsure how a library or PostgreSQL feature behaves, say so and cite the official documentation instead of guessing.
- Do not generate code you cannot explain. Add comments only where the reason is not obvious from the code (for example why a constraint or retry exists).

## 10. AI collaboration log

After each working session, append an entry to `docs/ai-log.md`. This feeds the AI Collaboration Narrative in the README.

```
## YYYY-MM-DD: <task>
- Goal / prompt intent:
- What the AI produced:
- Problems found (wrong, missing, risky):
- How I verified (docs read, tests, manual check):
- What I changed or rejected, and why:
```

## 11. Definition of done

- Behavior matches `docs/requirements/requirements.md` (FR, error codes) and is covered by tests.
- Build, tests and formatting checks pass.
- No new dependency, no schema change outside a migration, no invariant in section 4 weakened.
- The relevant document is updated if a decision changed.

## 12. Mistakes and session handoff

- At the start of every session, read the "Active prevention rules" table in `docs/mistakes.md`. If `docs/handoff/` has files, follow the `session-handoff` skill (resume mode) before doing anything else.
- When something turns out wrong (AI output, a bug caught by a test or constraint, a document that contradicts the brief), use the `log-mistake` skill. Do not edit AGENTS.md yourself: propose the exact line and wait for approval.
- When the user asks to hand off, wrap up, or continue in a new session, use the `session-handoff` skill (write mode).
- Skills live in `.agents/skills/<skill-name>/SKILL.md`. GitHub Copilot and Google Antigravity both read this location.

### Working across several AI tools

The project is built with more than one tool (GitHub Copilot and Google Antigravity), switched when one runs out of quota. A new session starts with no memory of the previous one.

- This file is the single source of rules. Tool-specific files (for example `.github/copilot-instructions.md`) only point here; do not copy rules into them.
- Chat history is not a project record. Decisions go into `docs/` (requirements, design, ADRs), problems into `docs/mistakes.md`, session summaries into `docs/ai-log.md`.
- Commit working changes in small commits so the next session can rebuild context from `git log` and `git status`.
- When the session is getting long or quota is running low, write a handoff before continuing the task. A session that stops abruptly cannot write one.