# P01 — Foundation

## Objective

Establish repository layout, environment templates, version pins, and documentation entrypoints so later phases have a stable root.

## Prerequisites

- P00 planning structure exists and is consistent.

## Tasks (see TASKS.md)

- `P01-T01` Expand `.gitignore` for Java/Node/Flutter/Docker/IDE/env
- `P01-T02` Create target directory tree with `.gitkeep` where needed
- `P01-T03` Rewrite root `README.md` (purpose, structure, resume pointer)
- `P01-T04` Add `.env.example` (no secrets)
- `P01-T05` Add `docs/TECH_STACK.md` with pinned versions intent
- `P01-T06` Add root `AGENTS.md` pointing to playbook/state
- `P01-T07` ADR for env/config conventions if needed

## Expected files

- `.gitignore`, `README.md`, `AGENTS.md`, `.env.example`
- `backend/`, `frontend/`, `mobile/`, `deploy/`, `docs/` placeholders
- `docs/TECH_STACK.md`

## Acceptance criteria

- Layout matches playbook target structure.
- `.env.example` documents required vars without real secrets.
- README links to `.cursor/PROJECT_PLAYBOOK.md` and how to resume.
- `git status` shows intentional new files only.

## Verification

```bash
find . -maxdepth 2 -type d | sort
test -f .env.example && test -f AGENTS.md && test -f docs/TECH_STACK.md
git status
```

## Evidence required

Command output confirming directories/files exist; no secret values committed.

## Risks

- Over-scaffolding empty apps before toolchains ready — keep placeholders minimal until P02/P03.

## Phase complete when

All P01 tasks `DONE` with evidence; next is P02 Compose/Makefile foundation.

## Status (2026-10-28)

**COMPLETE** — see `TASKS.md` P01-T01…T07 evidence.
