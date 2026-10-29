# ADR-001: Modular monolith

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P00-T03, P01-T02, P03-*

## Context

Graduation project needs one deployable backend with clear boundaries, without the operational cost of microservices on a single EC2 demo host.

## Decision

Ship a **modular monolith**: one Spring Boot app, packages/modules by domain (catalog, cart, order, identity), shared DB, Redis for cache.

## Alternatives considered

1. Microservices per domain — rejected: excessive ops for semester demo, harder local Compose and EC2.
2. Anemic single-package CRUD app — rejected: weak OOP architecture for grading.

## Consequences

- Positive: one artifact, simpler Makefile/Compose/EC2; clear package boundaries for OOP marks.
- Negative: must enforce module discipline manually.
- Follow-ups: document package layout in backend README / P03 plan.
