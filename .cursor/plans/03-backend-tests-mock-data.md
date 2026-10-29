# P07–P08 — Backend tests and demo seed

## Objective

Reliable automated tests and deterministic demo data with a safe reset path.

## Prerequisites

- Core APIs from P04–P06 exist (tests may land incrementally per feature).

## P07 — Tests

- Unit tests (services, domain rules)
- Integration tests with Testcontainers (Postgres, Redis)
- Security tests (anonymous/customer/admin)
- Shared fixtures distinct from demo seed

## P08 — Seed / reset

- Deterministic seed (fixed UUIDs/emails/SKUs)
- Makefile targets: seed, reset (destructive explicit)
- Document demo accounts (password only via env/example, never production secrets)

## Acceptance criteria

- CI-equivalent `./mvnw test` green with evidence
- Seed creates known catalog + admin/customer users
- Reset recreates same baseline without leftover orphans
- Test data builders do not depend on demo seed SQL alone

## Verification

```bash
./mvnw test
make seed-demo   # when Makefile exists
make reset-demo  # destructive; confirm volume policy
```

## Risks

- Flaky Testcontainers on constrained hosts
- Accidental wipe of non-demo volumes — require explicit target names

## Phase complete when

P07/P08 tasks `DONE` with command evidence; reset documented in README/docs.
