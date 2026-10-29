# ADR-002: Environment configuration

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P01-T04, P01-T07, P02-*, P12-T01, P14-T01

## Context

API, web, mobile, Compose, and EC2 need different hosts/secrets without committing secrets or hardcoding localhost into remote deploys.

## Decision

- Single root `.env.example` documents all shared variables; real values live in untracked `.env`.
- Compose reads env for Postgres/Redis credentials and ports.
- React uses `VITE_API_BASE_URL` at build/dev time (path-absolute `/api/v1` for Compose stack — ADR-016).
- Flutter uses build-time `API_BASE_URL` (`dart-define` / Make). See **ADR-014** for emulator/device URLs, cleartext policy, and Make→dart-define precedence.
- EC2 Make targets require explicit `EC2_HOST` (and related vars); no localhost default for remote deploy.

## Alternatives considered

1. Per-app env files only — rejected as primary: harder for Compose/Make orchestration (may still add app-local overrides later).
2. Hardcoded URLs per profile in source — rejected: breaks device/EC2 demos.

## Consequences

- Positive: one documented contract; safer demos; Makefile can pass Flutter URL explicitly.
- Negative: developers must copy `.env.example` before first run.
- Follow-ups: validate required vars in Make targets as they are implemented.
