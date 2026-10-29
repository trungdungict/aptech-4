# ADR-010: Explicit deterministic demo seed (not Liquibase)

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P08-T01

## Context

Need repeatable demo users/catalog for local demos without polluting schema migrations or auto-running on every API start / production.

## Decision

1. Liquibase remains **schema-only** (roles lookup rows already in `010-identity` are reference data, not demo accounts).
2. Demo fixtures live in `com.aptech.aptech4.seed` (`DemoSeedService` + catalog constants).
3. Seed runs only when `aptech.seed.enabled=true` (Makefile `seed-demo` / explicit env). Default is **false**.
4. One-shot runs set `aptech.seed.exit-after=true` so the process exits after seeding.
5. Idempotent upserts by natural keys (email / slug / SKU). Row UUIDs are DB-generated on first insert and remain stable on re-seed (no duplicate rows).
6. Existing user **passwords are not overwritten** on re-seed (use scoped reset — ADR-011 / `make reset-demo`).
7. Active Spring profile `prod` refuses seed unless `allow-on-prod` (never documented for real use).

## Consequences

- Positive: safe, Makefile-driven, testable with Testcontainers; no shared-volume wipe.
- Negative: demo passwords must be supplied via env; operators must run `make seed-demo` after first migrate. See ADR-011 for reset semantics.
