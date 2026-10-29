# ADR-009: Dual-mode Postgres/Redis integration tests

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P07-T01

## Context

Feature ITs (auth, catalog, cart, checkout, orders) already run against Compose Postgres/Redis via `IT_*` env when Maven executes inside `aptech4_net`. Nested Testcontainers (Maven-in-Docker spawning sibling containers via Docker Desktop sock) has been unreliable (B-001). Host JDK is also missing, so verification usually uses a Temurin Maven container.

## Decision

1. **Canonical Testcontainers sample** (`TestcontainersBootstrapTest`): starts ephemeral `postgres:16-alpine` + `redis:7-alpine` and boots the full Spring context. Used to prove Testcontainers wiring and Liquibase validate.
2. Pin **Testcontainers ≥ 1.21.4** (and `docker-java.properties` `api.version=1.44`) so Docker Engine 29 / Desktop 4.5x+ accepts the client (older 1.21.3 negotiated API 1.32 → empty HTTP 400 Info).
3. **Compose/`IT_*` mode** remains the default for the broader IT suite when the runner is on Compose network (Docker Maven without reliable DinD).
4. Do **not** force-migrate every IT to Testcontainers in P07-T01; later tasks may opt in when host JDK or stable sock mounting is available.
5. Document both paths in `backend/docs/TESTING.md`. Never wipe shared Compose volumes for tests.

## Alternatives considered

1. Testcontainers-only for all ITs — rejected for now: Docker Desktop sock from Maven container is flaky; would block the proven Compose path.
2. Testcontainers Cloud / Ryuk-less CI only — deferred; overkill for semester local/demo.

## Consequences

- Positive: Sample IT proves TC dependency versions (1.21.3) against Boot 4.1.2; Compose suite stays green under B-001 mitigation.
- Negative: Two wiring styles until migration; agents must pass `IT_*` for full suite or mount docker.sock for the TC sample.
