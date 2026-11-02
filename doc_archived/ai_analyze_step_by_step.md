## AI Collaboration Narrative

### Phase A: Planning

**Tool used:** [tool / model name]

#### Step 1: Analyze the assessment brief

- **Goal:** turn the full Keyloop brief into a focused reference for Scenario A (The Unified Service Scheduler).
- **Input:** the original assessment document.
- **Output:** `docs/assessments/keyloop_assessment.md`
- **Prompt used:**

> You are a specialist in analyzing technical assessments and extracting requirements from them. I will give you the Keyloop technical assessment brief. Focus only on Scenario A: The Unified Service Scheduler, and produce a Markdown document containing: (1) a short summary of the task, (2) the explicit core requirements and acceptance criteria, (3) the deliverables and evaluation criteria shared by all scenarios, (4) ambiguities that need a documented assumption, (5) implicit quality expectations. Do not invent requirements that are not in the brief, and separate what the brief states from your interpretation.

- **Verification:** [what you checked, e.g. compared the summary against the original brief to make sure no requirement was dropped or invented]

#### Step 2: Write my own requirements, then use AI to review them

**2a. First draft (human-written).** I wrote my own requirements from my current assumptions:
`docs/requirements/requirements.md` (original version kept in git history as commit `<hash>`).

**2b. AI review.** I gave the AI two files as context, `keyloop_assessment.md` and my `requirements.md`, and asked it to find mismatches and gaps and to propose improvements.

- **Outputs:**
  - Improved requirements: `docs/requirements/requirements.md`
  - Explanation of why each point needed improvement: `docs/requirements/requirements-review.md`
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