# Scenario A — The Unified Service Scheduler
## Requirements Extraction (from `KL_ASM_SCE1.md`)

> Source: Keyloop Technical Assessment brief (`KL_ASM_SCE1.md`, original file not committed to this repository).
> Scope: **Scenario A only.** Content below is split into "what the brief states"
> (verbatim or near-verbatim) and "my interpretation" (inference/reading).

---

## 1. Task Summary

The brief asks the candidate to **build an Appointment Scheduler application to
replace manual booking systems**. The scheduler lets a user request a service
appointment for a specific vehicle, service type, and dealership at a desired
time, validates that the required resources are free in real time, and then
persists a confirmed appointment record.

- **Domain:** Ownership
- **Nature of work:** Design a system (Part 1) and fully implement one service
  layer while mocking/stubbing the other (Part 2).

---

## 2. Explicit Core Requirements & Acceptance Criteria

These are the three requirements stated directly under Scenario A. Each is
treated as an acceptance criterion for the implementation.

### CR1 — Resource-Constrained Booking
- **Brief statement:** "Allow a user to request a service appointment for a
  specific vehicle, service type, and dealership at a desired time."
- **Acceptance signal:** The system must accept an appointment request carrying
  at minimum: vehicle, service type, dealership, and desired time.

### CR2 — Real-Time Availability Check
- **Brief statement:** "Before confirming, check for the availability of both a
  ServiceBay and a qualified Technician for the entire service duration."
- **Acceptance signal:** Confirmation must be gated on a check that runs
  *before* the appointment is created, and must cover **two** resource types
  simultaneously:
  - a ServiceBay, and
  - a qualified Technician,
  over the **entire service duration** (not just a single time point).

### CR3 — Confirmed Appointment Record
- **Brief statement:** "Upon success, create a persistent Appointment record
  associating the customer, vehicle, technician, and service bay."
- **Acceptance signal:** On success, a durable (persistent) Appointment record
  is created that links together: customer, vehicle, technician, and service bay.

---

## 3. Shared Deliverables & Evaluation Criteria (apply to all scenarios)

These are **not** Scenario-A-specific; the brief lists them once and they apply
to whichever scenario is chosen.

### Deliverables (3 artifacts)
1. **System Design Document** — the architectural plan.
2. **Working Code** — a Git repository containing the chosen service
   implementation, which must include:
   - a `README.md` with clear build/run/test instructions,
   - a dedicated **AI Collaboration Narrative** section (strategy for guiding
     the AI; process for verifying/refining output; how final quality was
     ensured),
   - a **suite of tests** validating the core business logic.
3. **Video Submission (5–10 minutes)** covering: self + scenario intro,
   walkthrough of design & implementation highlights, 1–2 min AI collaboration
   story, a brief demo, and lessons learned / challenges faced.

### Part 1 — System Design Document (required contents)
- An architecture diagram.
- A brief description of each component's role.
- An explanation of the data flow.
- A list of chosen technologies with justifications.
- A strategy for observability (logging, metrics, tracing).
- A dedicated section describing **how GenAI was used** to assist in the design
  phase.

### Part 2 — Service Implementation (candidate's choice)
- Implement **one** service layer fully; **mock or stub** the other.
  - **If Backend:** expose a RESTful API and use a **persistent database**;
    stub the client-side layer (test harness, cURL examples, or a basic API
    contract such as an OpenAPI spec).
  - **If Frontend:** build a web app demonstrating the full user experience;
    mock the backend with static data, a mock API library, or a local JSON
    server.
- **Build for the Future:** design and implementation must consider
  scalability, performance, reliability, maintainability, and observability.

### Evaluation Framework (4 dimensions, shared)
1. **Problem Solving & System Design** — clarity, logic, and foresight of the
   architecture.
2. **Technical Execution** — quality, correctness, and testing of the
   implementation.
3. **AI Engineering & Verification** — strategy for directing AI; process for
   verifying, debugging, and owning the final solution.
4. **Communication & Presentation** — clarity and professionalism of the
   documentation and video.

---

## 4. Ambiguities Requiring a Documented Assumption

The brief explicitly notes that requirements "can be ambiguous" and asks the
candidate to "make a reasonable assumption and document it in your System
Design Document." The following ambiguities are present in Scenario A as
written (these are prompts to document assumptions — not invented requirements):

- **Time duration is unspecified.** CR1 asks for a "desired time"; CR2 refers
  to "the entire service duration," but no duration value, service-length
  mapping, or booking granularity (e.g., 30-min slots) is defined.
- **"Qualified Technician" is undefined.** No skill matrix, certification rules,
  or mapping between service type and technician qualification is provided.
- **Resource model is undefined.** No data model, count, or state for ServiceBays
  or Technicians is given (e.g., how availability is stored, how conflicts are
  detected).
- **Customer identity is unspecified.** CR3 requires associating the "customer,"
  but CR1 speaks of "a user"; the relationship and required customer attributes
  are not defined.
- **Conflict/booking semantics are undefined.** No rules for double-booking,
  cancellation, rescheduling, or how a check failure should be reported.
- **Scope of "one service layer" is a choice.** Whether to build backend or
  frontend is left to the candidate, so the boundary of the mock/stub is
  unspecified.
- **Persistence technology is open.** "Persistent database" is required for the
  backend path, but no specific database or constraints are named.

---

## 5. Implicit Quality Expectations

These are inferred from the brief's language and structure (clearly marked as
interpretation, not stated requirements):

- **Interpretation:** Correctness of the availability check is central — the
  "real-time" and "entire service duration" wording implies the check must be
  race-free / consistent, not a naive single-point-in-time lookup.
- **Interpretation:** Because "tests that validate the core business logic" are
  explicitly required, the resource-constraint and availability logic is the
  highest-tested surface.
- **Interpretation:** "Build for the Future" across scalability, performance,
  reliability, maintainability, and observability signals the design should be
  modular and not hard-wired to a single dataset or deployment.
- **Interpretation:** The heavy emphasis on GenAI (design section + AI
  Collaboration Narrative + "AI Engineering & Verification" as a scored
  dimension) means the *process* of directing and verifying AI output is scored
  as heavily as the code itself.
- **Interpretation:** Documentation quality is a scored dimension, so the
  System Design Document and README are deliverables in their own right, not
  afterthoughts.
- **Interpretation:** "Own the final solution" implies the candidate is expected
  to understand and be able to explain every part of the AI-generated code, not
  just submit it.

---

## 6. What the Brief States vs. My Interpretation (quick reference)

| Topic | Brief states (explicit) | My interpretation (inference) |
|---|---|---|
| Booking input | vehicle, service type, dealership, desired time | duration/granularity must be assumed |
| Availability check | ServiceBay **and** qualified Technician, full duration | must be consistent/race-free, not point-in-time |
| Technician fit | "qualified" only | skill/cert mapping must be assumed |
| Appointment record | customer, vehicle, technician, service bay | customer identity vs. user undefined |
| Persistence | required (backend path) | DB choice open; must be truly persistent |
| Testing | required for core business logic | availability/constraint logic is the key tested surface |
| GenAI | must be described & reflected on | process is a scored dimension, code is not the only score |
