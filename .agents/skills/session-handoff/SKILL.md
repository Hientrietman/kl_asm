---
name: session-handoff
description: Write a handoff document so a new AI session, possibly in a different tool, can continue the current work without re-explaining it, and resume safely from an existing handoff. Use when the user says handoff, hand off, wrap up the session, switch tool, continue in a new session, quota or tokens are running low, context is getting long, or when starting a session and a file exists in docs/handoff/. Produces docs/handoff/YYYY-MM-DD-HHMM-slug.md built from verified repository facts, not from memory.
---

# Session Handoff

A handoff lets a fresh session pick up exactly where this one stopped. The reader has no memory of this conversation, so the document must be self-contained, short and verified.

## Mode 1: Write a handoff

### Step 1. Collect facts from the repository, not from memory

Run these and use their real output:

- `git branch --show-current`, `git status --short`, `git log --oneline -10`
- The project's test command (`./mvnw clean verify`, see AGENTS.md section 2). Record pass/fail counts and any failing test names. If tests cannot run (for example Docker is not running), say so.
- The tool you are running in and the model, so the next session knows the context it inherits.
- `git diff --stat` for uncommitted work.

Never write "tests pass" or "done" unless a command just showed it.

### Step 2. Write the file

Path: `docs/handoff/YYYY-MM-DD-HHMM-<short-slug>.md` (create the folder if needed). Keep it under about 80 lines. Link to files and documents instead of copying their content.

Use this template:

```
# Handoff: <short title>
Date: <YYYY-MM-DD HH:MM>   Branch: <branch>   Last commit: <hash and message>

## Goal
<One or two sentences: what the overall task is and what "done" means. Reference FR/NFR/ADR ids.>

## State
- Done: <items, each verified>
- In progress: <item, exact point where it stopped>
- Not started: <items>
- Uncommitted changes: <from git status; or "none">
- Test status: <command, result, failing tests if any>

## Decisions made (and why)
- <decision>: <reason>. <where it is documented>

## Key files
- <path>: <why it matters>

## Known problems and traps
- <open bug, flaky test, tricky behavior>
- Related entries in docs/mistakes.md: <ids>

## Open questions for the user
- <question, with the options considered>

## Next steps (in order)
1. <concrete action, first command or file to open>
2. ...

## Do not redo
- <things already settled or tried and rejected, with the reason>
```

### Step 3. Quality checks before finishing

- Every claim in State is backed by a command run in this session.
- Next steps are concrete enough that a new session can start without asking "what did you mean".
- No secrets, tokens, passwords, or personal data from request examples.
- Decisions the user made are recorded as theirs. Do not present your own suggestions as decisions.
- If a mistake was found this session that is not yet in docs/mistakes.md, use the log-mistake skill first.
- Tell the user the path of the file and print its "Next steps" section.

## Mode 2: Resume from a handoff

When a session starts and `docs/handoff/` contains files, or the user says to continue from a handoff:

1. Read the newest handoff file and `docs/mistakes.md` (the "Active prevention rules" section).
2. Do not trust the handoff blindly. Compare it with reality: run `git status`, `git log --oneline -5` and the test command. Report any difference (new commits, missing files, failing tests) to the user before acting.
3. Restate in three or four lines: the goal, the current state, and the first next step. Ask for confirmation if anything is unclear or the state differs.
4. Continue from "Next steps", respecting "Do not redo".
5. At the end of the session, write a new handoff. Do not edit the old one.