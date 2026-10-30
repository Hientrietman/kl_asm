# Mistakes and Prevention Rules

Read "Active prevention rules" at the start of every session. Add entries with the `log-mistake` skill (`.agents/skills/log-mistake/SKILL.md`).

## Active prevention rules

| Id | Rule |
|----|------|
| M-001 | Always write repository paths in docs and AGENTS.md in full from the root (e.g. `docs/requirements/requirements.md`); verify each path with a file search after editing. |
| M-002 | Keep the stack identical in AGENTS.md §2, requirements ADR-004/ADR-007, design §7/§10 and `pom.xml`; grep for the Java and Spring Boot versions after any stack change. |

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
