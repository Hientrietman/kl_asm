# AI Collaboration Log

One entry per working session (AGENTS.md §10).

## 2026-10-29: Audit instruction, skill and document paths
- Goal / prompt intent: Check that every rule, skill and required document is reachable by its referenced path, and that the project matches the documented structure.
- What the AI produced: A path and consistency audit; fixes to AGENTS.md, requirements, design and assessment documents; this log and `docs/mistakes.md`.
- Problems found (wrong, missing, risky): Wrong paths in AGENTS.md and docs (M-001); Java 17 vs 21 contradiction and incomplete stack list (M-002); `docs/mistakes.md` and `docs/ai-log.md` missing; `pom.xml` and `application.yaml` still the Initializr skeleton.
- How I verified (docs read, tests, manual check): Compared each referenced path with the workspace file list; `java -version` showed 17.0.12.
- What I changed or rejected, and why: User chose Java 17 everywhere (matches installed JDK and `pom.xml`). Did not create `KL_ASM_SCE1.md` (original brief not available) or rename `docs/assements/`; build setup in `pom.xml` left for a later task.

## 2026-10-29: Phase 0 build foundation
- Goal / prompt intent: Make `./mvnw clean verify` run against a real PostgreSQL and prove the ADR-003 exclusion constraints before any feature code.
- What the AI produced: `pom.xml` dependencies and plugins (Failsafe, JaCoCo, Spotless/palantir), `application.yaml` (+ `local`, `docker` profiles), `ClockConfig`, `docker-compose.yml` (db only), `.env.example`, Flyway `V1__create_schema.sql` and `V2__add_exclusion_constraints.sql`, `TestcontainersConfiguration`, `KlasmApplicationIT`, `SchemaConstraintsIT`, `ArchitectureTest`.
- Problems found (wrong, missing, risky): ArchUnit field-injection rule failed on an empty class set (needs `allowEmptyShould(true)` until modules exist). springdoc 3.x targets Boot 4, so the 2.x line was pinned. Running from the IDE gave `ClassNotFoundException` after the pom change (stale IDE classpath, not a build problem). Maven warns about a 401 from the flyway-community-db-support GitHub repository; harmless.
- How I verified (docs read, tests, manual check): `.\mvnw.cmd clean verify` BUILD SUCCESS: 4 unit tests, 6 integration tests (overlap rejected per bay/technician/vehicle with SQLSTATE 23P01 and constraint name, adjacent ranges allowed, cancelled rows do not block). `docker compose up -d db` + `spring-boot:run`: Flyway applied V1/V2, `/actuator/health/readiness` returned UP. Versions checked on Maven Central and the springdoc compatibility table.
- What I changed or rejected, and why: Moved the context test to `KlasmApplicationIT` so `./mvnw test` stays Docker-free. Kept `CREATE EXTENSION btree_gist` in V2 as written in ADR-003. Local DB credentials default only in `application-local.yaml`; the base config requires env vars. OTLP exporter deferred to Phase 5.

## 2026-10-29: B2 error handling and X-Trace-Id
- Goal / prompt intent: Every error is RFC 9457 Problem Details with a catalog `code` and `traceId`; every response carries `X-Trace-Id` (ADR-011, NFR-3, NFR-11).
- What the AI produced: `ErrorCode` (full catalog), `ApiException`, `FieldViolation`, `GlobalExceptionHandler` (extends `ResponseEntityExceptionHandler`), `CurrentTraceId`, `TraceIdFilter`, `ErrorHandlingIT` with a test-only controller.
- Problems found (wrong, missing, risky): Framework default details can echo library messages, so 400/404/500 details are replaced with fixed texts. Statuses with no catalog code (405, 415, 406) get no `code`; open question for the user. `@SpringBootTest` disables tracing by default, so the IT needs `@AutoConfigureObservability`.
- How I verified (docs read, tests, manual check): `.\mvnw.cmd clean verify` BUILD SUCCESS: 4 unit, 14 integration tests. `ErrorHandlingIT` covers malformed JSON, invalid body fields, invalid query parameter, malformed UUID, domain 404, unknown route 404, 500 without internal details, trace id header on success; each error asserts `application/problem+json`, `code`, and `traceId` equal to `X-Trace-Id`.
- What I changed or rejected, and why: Test controller is a nested class imported explicitly, so it is not component-scanned into other test contexts.
