# P02–P06 / P13 — Backend, database, auth, catalog, cart, cache

## Objective

Implement Spring Boot modular monolith: Liquibase schema, JWT auth/RBAC, catalog, cart/checkout/orders, Redis caching.

## Prerequisites

- P01 foundation directories and `.env.example`
- P02 Docker Postgres/Redis available for migrations/tests
- JDK 21 or Docker image for build/test (Docker Temurin path verified 2026-10-28)

## Phase breakdown

### P03 — Schema + domain architecture

- Spring Boot **4.1.2** scaffold + health (**P03-T01/T05 DONE**)
- Liquibase changelogs: users, roles, products, categories, cart, inventory, orders (**next: P03-T02**)
- Domain entities + repositories + layered packages
- OpenAPI baseline

### P04 — Auth

- Register/login, access JWT, refresh rotation/revocation, RBAC
- Security filter chain; ownership checks

### P05 — Catalog

- Public product/category list/search/pagination
- Admin CRUD for products/categories

### P06 — Cart / checkout / orders

- Cart lines, stock reservation in transactions, mock payment, order lifecycle

### P13 — Redis

- Cache product/category reads; invalidate on admin mutations

## Expected components

`backend/src/main/java/.../{identity,catalog,cart,order,common,config}`  
`backend/src/main/resources/db/changelog/**`  
Security + Redis config; REST controllers under `/api/v1`

## Acceptance criteria (summary)

- Fresh DB migrates cleanly via Liquibase
- Auth flows issue/refresh/revoke tokens; roles enforced
- Catalog + cart + mock checkout work via API
- Cache hit/miss + invalidation verified
- OpenAPI reflects endpoints

## Verification (examples)

```bash
cd backend && ./mvnw -q test
docker compose exec postgres psql -U ... -c '\dt'
# targeted security/catalog/order tests
```

## Risks

- Missing JDK → use `docker run` Temurin 21 Maven or mark verification `IN_REVIEW`
- Inventory races → transactional stock decrement ADR

## Phase complete when

Related `TASKS.md` IDs for P03–P06 and P13 are `DONE` or explicitly `DEFERRED` with reason.
