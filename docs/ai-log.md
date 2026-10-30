# AI Collaboration Log

One entry per working session (AGENTS.md §10).

## 2026-10-29: Audit instruction, skill and document paths
- Goal / prompt intent: Check that every rule, skill and required document is reachable by its referenced path, and that the project matches the documented structure.
- What the AI produced: A path and consistency audit; fixes to AGENTS.md, requirements, design and assessment documents; this log and `docs/mistakes.md`.
- Problems found (wrong, missing, risky): Wrong paths in AGENTS.md and docs (M-001); Java 17 vs 21 contradiction and incomplete stack list (M-002); `docs/mistakes.md` and `docs/ai-log.md` missing; `pom.xml` and `application.yaml` still the Initializr skeleton.
- How I verified (docs read, tests, manual check): Compared each referenced path with the workspace file list; `java -version` showed 17.0.12.
- What I changed or rejected, and why: User chose Java 17 everywhere (matches installed JDK and `pom.xml`). Did not create `KL_ASM_SCE1.md` (original brief not available) or rename `docs/assements/`; build setup in `pom.xml` left for a later task.
