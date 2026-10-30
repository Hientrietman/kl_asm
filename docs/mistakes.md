# Mistakes and Prevention Rules

Read "Active prevention rules" at the start of every session. Add entries with the `log-mistake` skill (`.agents/skills/log-mistake/SKILL.md`).

## Active prevention rules

| Id | Rule |
|----|------|
| M-001 | Always write repository paths in docs and AGENTS.md in full from the root (e.g. `docs/requirements/requirements.md`); verify each path with a file search after editing. |
| M-002 | Keep the stack identical in AGENTS.md §2, requirements ADR-004/ADR-007, design §7/§10 and `pom.xml`; grep for the Java and Spring Boot versions after any stack change. |
| M-003 | Concurrent conflicting inserts on a GiST exclusion constraint can fail with `40P01` (deadlock), not only `23P01`; the 20-request concurrency test must assert zero 5xx and every loser gets a catalog 409. |

## Entries

### M-001: Wrong file paths in AGENTS.md and docs
- Date: 2026-10-29
- Area: docs
- Severity: medium
- Source: human draft
- What happened: AGENTS.md referenced `docs/requirements.md` and `system_design.md`; the real files are `docs/requirements/requirements.md` and `docs/designs/system_design_documents.md`. `keyloop_assessment.md` referenced `docs/Assements/KL_ASM_SCE1.md`, which does not exist. The design document referenced `requirements.md` without a folder.
- Root cause: Documents were moved into subfolders after the references were written; paths were not re-checked.
- How it was detected: Review of every referenced path against the workspace file list.
- Fix: Paths corrected to full root-relative paths; the brief reference marked as not committed.
- Prevention rule: Always write repository paths in full from the root and verify each with a file search after editing.
- Occurrences: 1 (2026-10-29)
- Promoted to AGENTS.md: no

### M-002: Java version contradiction between AGENTS.md and design documents
- Date: 2026-10-29
- Area: requirements
- Severity: medium
- Source: human draft
- What happened: AGENTS.md and `pom.xml` used Java 17, while requirements ADR-004/ADR-007 and design §7/§10 specified Java 21 and a Temurin 21 image. AGENTS.md §2 also omitted libraries required by the docs (Actuator, Prometheus registry, OpenTelemetry bridge, ArchUnit, MockMvc/REST Assured).
- Root cause: The stack was changed in one document without updating the others.
- How it was detected: Cross-document review; installed JDK is 17.0.12.
- Fix: User chose Java 17 everywhere; docs updated to Java 17 / Temurin 17; AGENTS.md §2 stack list completed.
- Prevention rule: Keep the stack identical across AGENTS.md §2, ADR-004/ADR-007, design §7/§10 and `pom.xml`; grep for versions after any stack change.
- Occurrences: 1 (2026-10-29)
- Promoted to AGENTS.md: no

### M-003: Exclusion-constraint inserts deadlock under concurrency; design assumed only 23P01
- Date: 2026-10-29
- Area: database
- Severity: high
- Source: design-time risk
- What happened: ADR-003, FR-2.8 and AGENTS.md §5 assume a losing concurrent insert fails with SQLSTATE `23P01` and is retried. In `BookingConcurrencyIT` (20 parallel requests for the same slot) exactly one request returned `201`, but the other 19 returned `500`: their `INSERT INTO appointment` failed with `40P01 deadlock detected`. The test also took about 20 s because each deadlock is only detected after `deadlock_timeout` (1 s).
- Root cause: For a GiST exclusion constraint PostgreSQL inserts the index entry first and checks for conflicts afterwards; two in-progress transactions that see each other's entry wait for each other, and the deadlock detector aborts one. Unique B-tree indexes check before inserting, which is why the pattern was not anticipated.
- How it was detected: `BookingConcurrencyIT`, build output `PSQLException: ERROR: deadlock detected` on the appointment insert.
- Fix: Resolved by acquiring transaction-scoped advisory locks for the selected bay, technician, and VIN in sorted key order before customer/vehicle resolution and insert. The exclusion constraints remain authoritative, and retries remain limited to `23P01`. `BookingConcurrencyIT` verifies the 20-request behavior with no 5xx responses.
- Prevention rule: Never assume one SQLSTATE for constraint races; prove the error path with the parallel test and assert that no response is 5xx.
- Occurrences: 1 (2026-10-29)
- Promoted to AGENTS.md: yes, section 5
