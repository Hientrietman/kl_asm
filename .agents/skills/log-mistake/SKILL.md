---
name: log-mistake
description: Record a mistake in docs/mistakes.md so it does not happen again, and decide whether it should become a permanent rule in AGENTS.md. Use when AI output turns out wrong, the user corrects the AI, a test or database constraint exposes a bug, a review finds a flaw, a document contradicts the brief or another document, or the user says log this mistake or lessons learned.
---

# Log a Mistake

`docs/mistakes.md` is the project's memory of what went wrong. It has two parts: a short "Active prevention rules" list at the top (read at the start of every session) and detailed entries below.

## Step 1. Confirm it is a real mistake

Do not log guesses. Make sure there is evidence: a failing test, an official document that says otherwise, a command output, or the user's explicit correction. State the evidence in the entry.

## Step 2. Check for an existing entry

Search `docs/mistakes.md` for the same root cause. If it exists, do not create a duplicate: increase `Occurrences`, add the date and a one-line note, and go to Step 5.

## Step 3. Add a new entry

Use the next id (`M-NNN`) and append to the entries section:

```
### M-NNN: <short title>
- Date: YYYY-MM-DD
- Area: <requirements | design | database | api | testing | docs | process>
- Severity: <low | medium | high>   (high = can break an invariant in AGENTS.md section 4)
- Source: <AI output | human draft | design-time risk | tool behavior>
- What happened: <the wrong behavior or content, concretely>
- Root cause: <why it happened>
- How it was detected: <test, review, documentation, user correction>
- Fix: <what was changed>
- Prevention rule: <imperative sentence that a future session can follow and check>
- Occurrences: 1 (YYYY-MM-DD)
- Promoted to AGENTS.md: <no | yes, section N>
```

Rules for a good prevention rule: it is an instruction ("Always ... / Never ..."), it is specific to this project, and it says how to check it (a test, a grep, a review question). "Be careful with X" is not a rule.

## Step 4. Update the summary list

Add or update the one-line rule in "Active prevention rules" at the top of the file, with the entry id. Keep each line under about 200 characters. Remove a line only when the user agrees the risk is gone.

## Step 5. Decide on promotion to AGENTS.md

Propose promotion to `AGENTS.md` when any of these is true: severity is high, the mistake occurred twice or more, or it violates an invariant (AGENTS.md section 4 or 5). Show the user the exact line you would add and the section, and wait for approval. Do not edit AGENTS.md without approval. Keep AGENTS.md short: promote the rule, not the story.

## Step 6. Cross-reference

- Add a line to `docs/ai-log.md` under the current session: "Problems found" and "How I verified" should mention the entry id.
- If a handoff is being written, list the entry id under "Known problems and traps".
- Tell the user in two or three lines: the id, the rule, and whether promotion is proposed.

## Do not

- Do not blame or write vague statements such as "AI was wrong". Name the exact wrong content and the correct behavior.
- Do not include secrets or personal data.
- Do not delete old entries. Mark them as resolved in the "Fix" field if needed.