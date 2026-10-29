# ADR-003: Spring Boot 4.1.x (not 3.x)

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P03-T01, P00 docs update

## Context

Master prompt specified Spring Boot 3.x. On 2026-10-28, Spring Initializr only offers Boot **≥ 4.0.0** (default **4.1.2**). Generating a supported, pin-compatible project from Initializr therefore targets Boot 4.

## Decision

Pin **Spring Boot 4.1.2** with **Java 21** via Maven Wrapper. Update playbook/rules/tech stack to say Boot 4.x. Keep modular monolith and other guardrails unchanged.

## Alternatives considered

1. Hand-author Boot 3.5.x POM from Maven Central — rejected for now: unsupported by current Initializr; higher drift risk for a semester project in late 2026.
2. Wait / freeze older Initializr — rejected: not available.

## Consequences

- Positive: current toolchain, starter coordinates (`webmvc`, `liquibase` starters), long-term support path.
- Negative: docs/examples written for Boot 3 may differ (package/starter renames).
- Follow-ups: verify security/JWT libraries compatible with Boot 4 during P04; adjust Testcontainers BOM in P07.
