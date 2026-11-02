## AI Collaboration Narrative — Step by Step

This document traces, in chronological order, every stage where AI was used in the project, what it produced, what was wrong, how it was verified, and what the human decided. The archived brainstorming files that fed each step are in `doc_archived/`; the finalized documents that actually drive the implementation are in `docs/`.

**Tools used:** Claude (chat, for design-phase analysis and review), GitHub Copilot (IDE, for early edits), Google Antigravity (IDE, for all implementation from Phase 0 onward).

---

### Phase A: Planning

#### Step 1: Analyze the assessment brief

- **Goal:** turn the full Keyloop brief into a focused reference for Scenario A (The Unified Service Scheduler).
- **Input:** the original assessment document (`doc_archived/assements/kl_asm_sce1.md`).
- **Output:** `doc_archived/assements/keyloop_assessment.md` — a structured extraction with explicit requirements, ambiguities, and a "brief states vs. my interpretation" table.
- **Prompt used:**

> You are a specialist in analyzing technical assessments and extracting requirements from them. I will give you the Keyloop technical assessment brief. Focus only on Scenario A: The Unified Service Scheduler, and produce a Markdown document containing: (1) a short summary of the task, (2) the explicit core requirements and acceptance criteria, (3) the deliverables and evaluation criteria shared by all scenarios, (4) ambiguities that need a documented assumption, (5) implicit quality expectations. Do not invent requirements that are not in the brief, and separate what the brief states from your interpretation.

- **Verification:** Compared the AI summary against the original brief line by line. Confirmed CR1, CR2, CR3 were captured verbatim. Confirmed the ambiguities section listed only genuinely open points (duration, qualified technician, resource model, customer identity, conflict semantics) and did not introduce invented requirements.
- **What I kept:** The "Brief states vs. My interpretation" table (§6 of the output) — it became a working reference for every later decision.
- **What I changed:** Nothing significant. The AI stayed within the brief.

#### Step 2: Write my own requirements, then use AI to review them

**2a. First draft (human-written).** I wrote my own requirements based on my reading of the brief and my assumptions about how a scheduling system should work: `doc_archived/requirements/requirements.md` (the original version).

**2b. AI review.** I gave the AI two files as context — `keyloop_assessment.md` and my `requirements.md` — and asked it to find mismatches and gaps and to propose improvements.

- **Outputs:**
  - Improved requirements: `doc_archived/requirements/requirements_improved.md`
  - Explanation of why each point needed improvement: `doc_archived/requirements/requirements_rationale.md`
- **Prompt used:**

> You are a software engineer with 2-4 years of experience. I will give you an assessment brief for a Unified Service Scheduler and my own requirements. Based on these two, analyze any mismatches, improvements, and anything that should be more specific. Then create two files: one with the improved requirements, and one explaining why each of my points needed improvement.
>
> For example: in my FR-1 (Resource Constrained Booking), the VIN should have a length constraint. A-2 ("Service bay and technician are explicitly selected") forces the user to pick the service bay and technician, but in the assessment it state that the user only need to
> Also complete the ARD using the same format and dont use any icons

**2c. Human review of the AI output.** I did not take the AI's draft as final. I read it end to end, checked each claim against the brief, and kept only what actually held up. The AI was a fast first pass, but the decisions were mine.

- **Accepted (and why):**
  - **VIN length/format validation** — the brief treats the vehicle as a core input, so a 17-character ISO 3779 constraint was the obvious fix. Kept as-is.
  - **Availability-driven selection instead of explicit user selection (A-2)** — the AI's read matched mine: the brief says the system must "check for the availability of both a ServiceBay and a qualified Technician," which only makes sense if the system *finds* the resources. A client pre-selecting a bay/technician would defeat the whole point of a real-time check. I let the client express a preference, but only use it when the resource is actually free.
  - **Splitting the error codes** — the original returned `409` for everything. I agreed that validation, not-found, and conflict are genuinely different and need `400`/`404`/`409`.
  - **Two separate exclusion constraints** — one on the service bay, one on the technician. A single generic `(resource_id, range)` constraint was too vague for a model with two resources that each need to be non-overlapping.
  - **Half-open time ranges `[start, end)`** and excluding `CANCELLED` appointments from the availability check — both are standard scheduling conventions and worth documenting explicitly.

- **Rejected or changed:**
  - **Stack was never in question.** The AI floated a few options, but Spring Boot + Java was my call from the start, so I left that decision untouched.
  - **I tightened several of the AI's suggestions rather than adopting them wholesale.** For example, it mentioned "1-minute granularity," but that phrasing clashed with the "no fixed slots" idea, so I rewrote it as continuous time with a 1-minute smallest unit to remove the contradiction.
  - **I added things the AI missed** — the resource catalog (FR-5) and concurrency/idempotency (FR-6) were absent from its draft, but the availability check can't work without a catalog, and NFR-1 (no double-booking under concurrency) needs an explicit guarantee. Those became my additions, not the AI's.

- **Errors I caught in the AI's content:**
  - **ADR-004 referenced `class-validator`** — that's a NestJS/TypeScript library, not something a Spring project uses. Flagged it and replaced it with Bean Validation (`jakarta.validation`).
  - **ADR-006 was titled "Pino," a Node.js logger** — incompatible with a Java codebase, and the row was completely empty. Rewrote it around Logback + Logstash.
  - **ADR-005 had typos** ("Srping") and a broken decision line. Cleaned up.
  - **Every ADR carried emoji** (`✅`/`⚠️`). The brief asks for professional docs, so I stripped them all and gave each ADR the same column layout.

**Lesson:** The AI is good at producing a first draft quickly, but it also carries its own assumptions and can copy in the wrong library names. I treated its output as a starting point to verify, not a finished answer — every requirement I kept I could trace back to the brief or to a gap I spotted myself.

---

#### Step 3: Consolidate brainstorming into the finalized project documents

- **Goal:** Take the brainstormed and reviewed outputs from Steps 1–2 (the three `doc_archived/requirements/` files and the draft `doc_archived/designs/system_design_documents.md`) and produce the single, authoritative pair of documents that the implementation would actually follow.
- **Inputs:** `doc_archived/requirements/requirements_improved.md`, `doc_archived/requirements/requirements_rationale.md`, `doc_archived/designs/system_design_documents.md`, and my notes from Step 2c.
- **Outputs:** `docs/requirements/requirements.md` and `docs/designs/system_design_documents.md` — the finalized contract.
- **What I asked the AI to do:**

> Here are three files: my improved requirements (after your review), the rationale document, and the draft system design. Merge them into two clean, final documents: (1) a single `requirements.md` that incorporates every accepted change from Step 2c and drops the rejected ones, and (2) a `system_design_documents.md` that references the requirements by id and is internally consistent. Fix the Java version to 17 everywhere. Fix the Spring Boot version to 3.5 or later. Remove all references to NestJS, Pino, or class-validator. Use the same ADR table format throughout. Do not invent new requirements.

- **What the AI produced:**
  - A consolidated `requirements.md` (version 2.0) with: a traceability table mapping every brief item to an FR/NFR, a domain model section with exact column constraints, a complete error catalog, 17 documented assumptions (A-1 to A-17), and 12 ADRs in a uniform format.
  - A consolidated `system_design_documents.md` with: architecture and data-flow diagrams (Mermaid), component descriptions referencing the requirement ids, consistency/concurrency strategy, deployment diagram, and a GenAI section (§12) with placeholders for the implementation narrative.

- **Verification:**
  - Cross-checked every FR, NFR, assumption, and ADR against the brief and the rationale document.
  - Confirmed the traceability table covered all three core requirements (CR1–CR3), all five "Build for the Future" qualities, and the testing requirement.
  - Confirmed the three exclusion constraints now had `WHERE (status = 'CONFIRMED')` — the critical fix from the rationale (TI-1).
  - Confirmed ADR-004 no longer mentioned `class-validator` and ADR-006 no longer mentioned Pino.
  - Confirmed Java version was 17 everywhere (it was not — the design §7 and §10 still said Java 21 and Temurin 21). This became the first entry in `docs/mistakes.md` (M-002).

- **What I changed after the AI's consolidation:**
  - **Java/Spring version alignment (M-002).** The AI left "Java 21" in the design document even though I asked for Java 17. I corrected it in §7 (Technology Choices) and §10 (Deployment) and added M-002 as a prevention rule: keep the stack identical across all documents and grep for versions after any change.
  - **15-minute grid instead of 1-minute.** The AI's consolidation kept my 1-minute granularity from Step 2, but during the design review I changed my mind: 15-minute boundaries keep the availability query finite and prevent fragmented gaps. This became A-4 and ADR-008.
  - **Dealership time zone.** The archived design said "UTC only, client does conversion." I added an IANA time zone per dealership because working hours are local and daylight saving time shifts offsets. This became A-7 and ADR-008.
  - **Vehicle exclusion constraint.** The rationale (TI-3) flagged that the vehicle had no overlap protection. I accepted this and added it as a third exclusion constraint, FR-2.6, and A-14.

- **Lesson:** Even when you give the AI explicit instructions ("fix Java to 17"), it can miss some occurrences. The prevention rule (M-002) saved time in later sessions by making cross-document consistency a checklist item rather than a memory exercise.

---

#### Step 4: Write AGENTS.md and project skills for AI-assisted implementation

- **Goal:** Before writing any code, create a structured instruction file (`AGENTS.md`) and reusable skills (`.agents/skills/`) so that any AI tool — and any future session that starts with no memory — can pick up the project and follow the same rules.
- **Why this step matters:** The assessment scores "AI Engineering & Verification" as a full dimension. The instructions are not just documentation; they are the mechanism that makes AI collaboration repeatable and auditable. Without them, each session would re-derive rules from scratch and repeat mistakes.
- **What I wrote (human, not AI-generated):**

  - **`AGENTS.md`** — 12 sections covering:
    - §1 Source of truth (which documents are the contract).
    - §2 Fixed technology stack (exact versions; "do not add dependencies without asking").
    - §3 Architecture rules (modular monolith, package-by-feature, constructor injection, no entities in the API).
    - §4 Non-negotiable invariants (the three exclusion constraints, half-open ranges, `timestamptz`, injected `Clock`, Flyway-only schema changes, booking never modifies customer/vehicle).
    - §5 Booking transaction rules (each attempt in its own transaction, advisory locks in sorted key order, retry only on `23P01`, constraint name → error code).
    - §6 API conventions (RFC 9457, error catalog, `X-Trace-Id`).
    - §7 Logging and observability (structured JSON, no PII, metric names from the design).
    - §8 Testing rules (Testcontainers, no H2, never weaken a test, concurrency test contract).
    - §9 How to work (plan first, small steps, run the full suite, cite docs).
    - §10 AI collaboration log (`docs/ai-log.md` format).
    - §11 Definition of done.
    - §12 Mistakes and session handoff (read `docs/mistakes.md` first, use skills).

  - **`.agents/skills/log-mistake/SKILL.md`** — a structured process for recording mistakes so they become prevention rules, not just one-off fixes. Fields: date, area, severity, source, what happened, root cause, detection, fix, prevention rule, promotion decision.

  - **`.agents/skills/session-handoff/SKILL.md`** — a template for writing a handoff document (`docs/handoff/`) when switching AI tools or running out of context. The handoff captures: what was done, what is in progress, what is blocked, environment details, and open decisions. The next session reads it before doing anything.

- **How I verified these were useful:**
  - The `log-mistake` skill was used four times during implementation (M-001 through M-004). Each entry produced a prevention rule that either went into AGENTS.md (M-003) or stayed as a checklist item.
  - The `session-handoff` skill was used once (commit `6fbfec7`) when switching from one AI tool to another mid-B4. The receiving session resumed without re-explaining the project.
  - AGENTS.md §4 and §5 directly prevented the AI from weakening the exclusion constraint tests or silently catching generic exceptions — both things it attempted in early B4 drafts.

- **Lesson:** Writing the rules before the code costs an hour. Not writing them costs a day of debugging the same mistake in three different sessions.

---

### Phase B: Implementation (Vertical Slices)

#### Step 5: Plan the vertical-slice approach

- **Goal:** Break the implementation into independently testable slices, ordered so each slice builds on the previous one and the hardest problem (concurrency) is solved early.
- **What I asked the AI to do:**

> Given these requirements (FR-1 to FR-6, NFR-1 to NFR-11) and this architecture (modular monolith, 5 modules), propose an implementation plan as a sequence of vertical slices. Each slice must: touch one concern, be independently testable with `./mvnw clean verify`, and produce a commit. The hardest technical risk — the concurrency invariant (NFR-1) — should be proven as early as possible.

- **What the AI proposed (and what I adjusted):**

| Slice | AI's proposal | My adjustment |
|-------|--------------|---------------|
| Phase 0 | Schema + migrations + Testcontainers + build plugins | Accepted. Added: prove the exclusion constraints with an integration test *before* any feature code. This is the single most important test in the project. |
| B1 | Seed reference data + read-only endpoints (FR-6) | Accepted. Moved *before* the booking endpoint because the booking request references dealerships, bays, technicians, and service types — without B1 there is nothing to book against. |
| B2 | Error handling (RFC 9457 + `X-Trace-Id`) | Accepted. The AI wanted this after B4, but I moved it before B3 because validation (B3) and booking (B4) both return errors, and I wanted the error format tested once and reused. |
| B3 | Input validation (FR-1 field rules + booking window + working hours) | Accepted. Pure logic, no database writes, unit-testable. |
| B4 | Booking with auto-assignment (FR-2, NFR-1) | Accepted. This is where the concurrency contract is proven. The AI's first version failed (M-003 — deadlocks). The fix (advisory locks) was a joint human-AI effort. |
| B5 | Idempotency (FR-3.4) | Accepted. |
| B6 | Get, list, cancel (FR-4) | Accepted. |
| B7 | Availability query (FR-5) + alternatives (FR-2.7) | Accepted. |
| B8 | Observability (custom metrics, PII-free logging) | Accepted. |
| Phase C | Dockerfile, docker-compose, README, OpenAPI, load test | Accepted. |

- **Why vertical slices and not horizontal layers:** Each slice produces a working, tested increment. If the project ran out of time at B5, the core booking with concurrency safety would still be complete and demonstrable. A horizontal approach (all entities first, then all services, then all controllers) would have left the concurrency question unanswered until the end.

---

#### Step 6: Execute each slice with AI — the build log

Each slice below was implemented by directing the AI (Google Antigravity) with a specific prompt, reviewing the output, running the test suite, and fixing problems. The full session logs are in `docs/ai-log.md`. Here I highlight only the key decisions, mistakes, and corrections per slice.

**Phase 0 — Build foundation** (commit `3bebc67`)

- **Prompt intent:** "Make `./mvnw clean verify` run against a real PostgreSQL and prove the exclusion constraints before any feature code."
- **AI produced:** `pom.xml` dependencies, Flyway `V1__create_schema.sql` and `V2__add_exclusion_constraints.sql`, `TestcontainersConfiguration`, `SchemaConstraintsIT` (6 tests: overlap rejected per bay/technician/vehicle with correct SQLSTATE and constraint name, adjacent `[end, start)` ranges allowed, cancelled rows do not block).
- **Problems found:**
  - ArchUnit field-injection rule failed on an empty class set — needed `allowEmptyShould(true)` until modules exist.
  - springdoc 3.x targets Spring Boot 4; the 2.x line was pinned instead.
- **Verification:** `./mvnw clean verify` BUILD SUCCESS: 4 unit tests, 6 integration tests.
- **Lesson:** Proving the database constraints first gave confidence that every later slice could rely on them.

**B1 — Reference data** (commit `2f3a20e`)

- **AI produced:** `V3__seed_reference_data.sql` (2 dealerships with time zones and working hours, bays, technicians, qualifications, service types), modules `catalog` and `dealership`, `ReferenceDataIT`.
- **Problem caught:** The AI's first seed draft derived technician qualifications from the last character of the technician UUID — fragile and unreadable. Replaced with explicit rows.
- **Verification:** 4 unit, 21 integration tests.

**B2 — Error handling** (commit `a1b28b4`)

- **AI produced:** `ErrorCode` enum (full catalog), `GlobalExceptionHandler`, `TraceIdFilter`, `ErrorHandlingIT`.
- **Problem caught:** Framework default 400/404/500 detail messages can echo library internals. The handler now replaces them with fixed texts so no SQL or class names leak.
- **Verification:** 4 unit, 14 integration tests.

**B3 — Input validation** (commit `7ae5a66`)

- **AI produced:** `@Vin`, `@QuarterHourGrid`, `@VehicleYear` custom annotations, `CreateAppointmentRequest` with compact-constructor normalization, `BookingWindowPolicy`, working-hours validation.
- **Key decision:** The 15-minute grid is validated on the UTC instant's minutes, not on the local minute — so a client offset with fractional seconds cannot sneak an off-grid start through.
- **Verification:** 60 unit tests (VIN edge cases, grid boundaries, vehicle year, every request field, booking window, working hours including Saturday, closed Sunday, midnight span, London winter vs. summer offset, Ho Chi Minh City local day), 21 integration tests.

**B4 — Booking with auto-assignment** (commits `e7646b6`, `6fbfec7`)

This was the hardest slice and the one where AI collaboration was most visible.

- **AI produced:** Customer/vehicle find-or-create, availability candidate queries, `BookingService` with `TransactionTemplate` (REQUIRES_NEW per attempt), the concurrency test (`BookingConcurrencyIT` — 20 parallel requests).
- **Critical failure (M-003):** The concurrency test produced exactly one `201` (the invariant held), but the 19 losers got `500` — not `409`. The `INSERT` failed with `40P01 deadlock detected`, not `23P01 exclusion_violation`. The AI's design (and mine, and the requirements) had assumed only `23P01`.
  - **Root cause:** GiST exclusion constraints insert the index entry first and check for conflicts afterwards. Two concurrent transactions that each see the other's uncommitted entry wait for each other → deadlock.
  - **What the AI proposed as a fix:** Catch `40P01` as well and retry. I rejected this because it would mask a real deadlock.
  - **What I chose instead:** Transaction-scoped advisory locks for the bay, technician, and VIN, acquired in sorted key order *before* the insert. This serializes conflicting attempts without touching the constraint logic. The exclusion constraints remain the final authority.
  - **How I verified:** The concurrency test now asserts: exactly one `201`, exactly nineteen `409`, **zero `5xx`**. The advisory lock order prevents deadlocks; the constraint prevents double-booking.
- **Promoted to AGENTS.md §5** as a permanent rule. Logged as M-003 in `docs/mistakes.md`.
- **Session handoff:** This slice consumed so much context that I had to switch AI tools mid-way (commit `6fbfec7`). The `session-handoff` skill produced a handoff document; the next session resumed from it.

**B5 — Idempotency** (commit `fdefbbc`)

- **AI produced:** `RequestHash` (SHA-256 of normalized command), replay check before and after insert, `IdempotencyIT` (7 cases including 10 parallel requests with one key).
- **Key decision:** Hash over the normalized command (not raw body bytes) so whitespace/formatting differences in a retry are not treated as a different request.
- **Problem caught:** A race loser can be rejected by the vehicle exclusion constraint *before* the unique idempotency key, so the replay re-check runs after every conflict kind — not only after the key constraint.
- **Verification:** 87 unit, 50 integration tests.

**B6 — Get, list, cancel** (commit `7105790`)

- **AI produced:** `AppointmentQueryService` with JPA `Specification` filters, `CancellationService`, `AppointmentManagementIT` (11 cases).
- **Problem caught:** REST Assured adds a form content type to an empty POST body, causing `415`. The endpoint itself accepts a missing body — the test was wrong, not the code. Fixed the test to send empty JSON.
- **Architecture decision:** Kept batch lookups (3 queries per page) instead of joining other modules' tables, to respect ADR-002 module boundaries.
- **Verification:** 87 unit, 61 integration tests.

**B7 — Availability query and alternatives** (commit `29ebcca`)

- **AI produced:** `SlotGrid` (pure: 15-minute local starts fitting the working day), `CandidateQueries.freeStarts` (one `generate_series` query), `AvailabilityController`, up to 3 alternative starts in `NO_AVAILABILITY` responses.
- **Problem caught (M-004):** A missing request parameter returned 400 *without* the `code` or `traceId` fields. Root cause: the B2 error handler enriched only non-null bodies, but Spring's `ResponseEntityExceptionHandler` passes `null` for some framework errors and builds the body later. Fix: resolve the body from `ErrorResponse.updateAndGetBody` before enriching. Logged as M-004.
- **Architecture decision:** `BookingWindowPolicy` was moved from the `appointment` module to the `availability` module to avoid a module dependency cycle. The availability module already depended on dealership and catalog; if appointment depended on availability and availability depended on appointment, ArchUnit would fail.
- **Verification:** 92 unit, 70 integration tests.

**B8 — Observability** (commit `de7008d`)

- **AI produced:** `BookingMetrics` (counters for `appointments.booked`, `booking.conflicts{reason}`, `booking.assignment.retries`; timer for `booking.duration`), PII-free logging (`LogMasking.vinSuffix`), `ObservabilityIT` (Prometheus scrape deltas, `CapturedOutput` asserts no email, phone, notes, or full VIN in logs).
- **Verification:** 92 unit, 72 integration tests.

---

### Phase C: Packaging and Delivery

#### Step 7: Package, document, and ship

- **Goal:** Make the project runnable with one command, fully documented, and ready for review.
- **What I asked the AI to do:**

> Complete Phase C: (1) multi-stage Dockerfile, (2) app service in docker-compose.yml, (3) export openapi.yaml from the running app, (4) comprehensive README with architecture, cURL examples, error catalog, and AI Collaboration Narrative, (5) k6 load-test suite for NFR-2.

- **AI produced** (commit `2498d6b`):
  - `Dockerfile` (multi-stage: Maven build → Temurin 17 JRE Alpine, non-root user `appuser`, healthcheck via wget).
  - `docker-compose.yml` app service with `depends_on: db (service_healthy)`.
  - `openapi.yaml` exported via `OpenApiExportIT`.
  - `README.md` with: architecture overview, quick start, 3-minute verification walkthrough, cURL examples for every endpoint, error catalog, observability section, test guide, and AI Collaboration Narrative.
  - `load-test/` suite: `seed-load-data.sql` (10,800 historical appointments), `booking-load.js` (k6 script, 20 VUs, p95 < 500 ms thresholds).

- **Additional deliverables** (commit `cca302f`):
  - Built-in live metrics dashboard at `/dashboard.html` (vanilla HTML/JS polling `/actuator/prometheus`).
  - Docker Compose `observability` profile with Prometheus + Grafana.

- **Problems found:**
  - Maven wrapper download failed in the offline sandbox — resolved by linking the cached distribution.
  - Docker daemon access on Windows requires unsandboxed execution.
  - `OpenApiExportIT` initially wrote the random test port into `openapi.yaml` — replaced with `http://localhost:8080`.
  - Generated `.java` file had LF line endings, triggering Spotless failure — resolved with `spotless:apply`.

- **Final verification:** `./mvnw clean verify` BUILD SUCCESS: **92 unit tests, 73 integration tests**, Spotless 101 files clean, JaCoCo report generated.

---

### Summary: How AI Was Used and How Quality Was Ensured

| Phase | AI's role | Human's role |
|-------|-----------|-------------|
| **Step 1** (brief analysis) | Extract and structure the brief | Verify nothing was dropped or invented |
| **Step 2** (requirements) | Review and improve my first draft | Accept, reject, or rewrite every suggestion; add missing requirements (FR-5, FR-6, concurrency); catch wrong library names (class-validator, Pino) |
| **Step 3** (consolidation) | Merge brainstorming into final docs | Fix version inconsistencies (M-002); change 1-minute grid to 15-minute; add vehicle constraint; add dealership time zone |
| **Step 4** (instructions) | None — written by human | Write AGENTS.md (12 sections), `log-mistake` skill, `session-handoff` skill |
| **Step 5** (planning) | Propose slice order | Move B2 before B3; insist on proving constraints in Phase 0 |
| **Step 6** (implementation) | Generate code, tests, migrations per slice | Run `./mvnw clean verify` after every slice; discover M-003 (deadlock, high severity), choose advisory locks over the AI's suggestion; discover M-004 (handler bug); enforce module boundaries |
| **Step 7** (packaging) | Generate Dockerfile, README, load test | Fix port in OpenAPI, fix line endings, verify one-command startup |

**Key numbers:**
- **4 mistakes logged** (M-001 to M-004), each with a root cause, prevention rule, and promotion decision.
- **1 mistake promoted to AGENTS.md** (M-003: advisory locks for GiST deadlocks) — a permanent project rule.
- **1 session handoff** written and successfully consumed by a different AI tool.
- **92 unit tests, 73 integration tests** — all passing against a real PostgreSQL via Testcontainers.
- **20-request concurrency test**: exactly 1 success, 19 conflicts, 0 server errors.

**The principle throughout:** the AI is a fast first-pass generator. Every output goes through the same verification loop: read it, check it against the brief and the requirements, run the tests, and decide what to keep. The AI never had the last word.
