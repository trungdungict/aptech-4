# ADR-011: Scoped demo reset (not database/volume wipe)

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P08-T02
- **Depends on:** ADR-010

## Context

Operators need a way to clear and recreate demo fixtures without destroying Compose volumes, non-demo rows, or production data.

## Decision

1. **Scope** = exact natural keys from ADR-010 seed: configured demo emails + `DemoSeedCatalog` slugs/SKUs. No `LIKE 'DEMO-%'` for users/categories; no truncate-all.
2. **Triggers** only via explicit `aptech.seed.reset=true` (Makefile `reset-demo` / `reset-demo-dry`). Never on normal API startup, seed, or tests.
3. **Preflight**: refuse Spring profile `prod`; require resolvable JDBC URL; dry-run prints target + counts; destructive path requires `reset-confirm=yes` (Makefile: `CONFIRM=yes`).
4. **Delete order** follows FK constraints (reservations → carts → orders/dependents → products/inventory → categories → users). Constraints stay enabled.
5. **`make reset-demo`** = confirmed delete + reseed. **`make reset-demo-dry`** = plan only. **`make clean`** = Maven artifacts only. **`make down`** = stop containers, keep volumes. Volume wipe remains the separate, explicitly confirmed `reset-deps-volumes`.

## Consequences

- Positive: demo baseline recoverable; shared volume safe; production fail-closed.
- Negative: rows created outside seed with coincidental emails/SKUs matching the catalog are treated as demo-owned.
