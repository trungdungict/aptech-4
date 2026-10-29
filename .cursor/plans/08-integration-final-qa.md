# P16–P17 — CI, integration, final QA, demo

## Objective

GitHub Actions CI, smoke E2E, complete docs, graduation demo checklist.

## Prerequisites

- Core backend + at least one client path working locally
- Makefile test targets exist

## P16

- CI workflow: backend tests, frontend tests/build, (flutter if runner supports)
- Smoke script against local compose
- Operator docs: runbook, resume, demo accounts reference

## P17

- End-to-end acceptance checklist executed
- Demo script (customer buy flow + admin update)
- Known limitations documented (HTTP EC2, mock payment)

## Acceptance criteria

- CI config present; local smoke evidenced
- P17 checklist items checked with dates/commands
- Playbook resume prompt still accurate

## Verification

```bash
# CI: push or act if available
make smoke-local   # when defined
```

## Phase complete when

Graduation demo can be rehearsed from docs alone; all critical path tasks `DONE` or accepted `DEFERRED`.
