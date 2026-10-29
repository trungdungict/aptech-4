# Backend testing

See ADR-009 for the dual-mode integration strategy.

## Unit tests

Run with Maven Wrapper (host JDK 21 or Docker Temurin image). No external Postgres/Redis required.

## Auth security suite (P07-T02)

| Area | Primary tests | Kind |
|------|---------------|------|
| JWT filter (missing / bad / wrong sig / issuer / `token_use` / expired / refresh-as-access) | `JwtSecurityWebTest`, `AuthSecurityRegressionTest`, `JwtServiceTest` | WebMvc slice + TC IT + unit |
| RBAC / ownership matrix | `RbacSecurityWebTest`, `AuthIntegrationTest` | WebMvc slice + Compose IT |
| Register/login/password hash / no role elevation | `AuthIntegrationTest` | Compose IT |
| Refresh rotate / reuse family revoke / concurrent / logout | `AuthIntegrationTest`, `AuthSecurityRegressionTest` | Compose IT + **Testcontainers** IT |
| Fail-fast secrets / CORS `*`+credentials | `JwtPropertiesContextFailFastTest`, `JwtPropertiesValidationTest` | Unit |
| CORS allow/deny + credentials | `JwtSecurityWebTest`, `AuthSecurityRegressionTest` | Slice + TC IT |
| Error hygiene (no stack/secrets/tokens) | `AuthSecurityRegressionTest` | TC IT |
| OpenAPI public | `OpenApiSecurityWebTest`, `AuthIntegrationTest` | Slice + Compose IT |

```bash
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
./mvnw -Dtest='*Security*,AuthIntegrationTest,JwtServiceTest,JwtProperties*' test
./mvnw test   # full regression
```

`AuthSecurityRegressionTest` uses Testcontainers Postgres 16 + Redis 7. Auth does **not** store sessions in Redis; Redis is required only because the app context starts a Redis client.

## Catalog / cart / order coverage matrix (P07-T03)

| Requirement | Covered by | Gap status |
|-------------|------------|------------|
| Public categories active-only | `CategoryIntegrationTest` | Closed |
| Public products active + active category; search/filter | `ProductIntegrationTest` | Closed |
| Pagination page≥0, default size 20, max 100 | `ProductIntegrationTest` | Closed (default size asserted) |
| Sort whitelist name/price/createdAt | `ProductIntegrationTest`, `ProductSortSupportTest` | Closed |
| Admin category/product RBAC + soft-disable + SKU/name conflict | `CategoryIntegrationTest`, `AdminProductIntegrationTest` | Closed |
| Cart ownership, merge qty, server BigDecimal, no stock mutation, inactive policy | `CartIntegrationTest` | Closed |
| Reservation available=on_hand−reserved; release idempotent; concurrent no oversell | `InventoryAvailableTest`, `InventoryReservationIntegrationTest` | Closed (concurrent on Compose PG) |
| Checkout empty cart; DB price; mock pay; cart clear on PAID; idempotency; concurrent | `CheckoutIntegrationTest` | Closed (concurrent on Compose PG) |
| Order ownership; admin status whitelist; snapshots; no payment forge via status | `OrderLifecycleIntegrationTest`, `OrderStatusTransitionsTest` | Closed |
| Auth security matrix | See P07-T02 section | Closed |
| Public catalog Redis cache hit/miss/key isolation/invalidation | `CatalogCacheIntegrationTest`, `CatalogCacheKeysTest`, `CacheErrorHandlerTest` | Closed (P13 / ADR-015) |

PostgreSQL semantics for locking/concurrency are exercised against **Compose Postgres 16** (`IT_*`) and/or **Testcontainers** (`AuthSecurityRegressionTest`, `TestcontainersBootstrapTest`). H2 is not used.

### JaCoCo

- Plugin: `jacoco-maven-plugin` 0.8.13
- `./mvnw test` → HTML report at `backend/target/site/jacoco/index.html`
- `./mvnw verify` → bundle **LINE ≥ 70%** floor (measured baseline ~82% LINE on 2026-10-28; not a substitute for invariant ITs)

### Surefire naming

Default Spring Boot Surefire includes `**/Test*.java`, `**/*Test.java`, `**/*Tests.java`. Prefer `*Test` / `*Tests` suffixes so classes are not silently omitted (e.g. `AuthSecurityRegressionTest`, not `…IT`).

## Integration tests — Compose `IT_*` (default suite)

Feature ITs (auth, catalog, cart, checkout, orders) bind to Compose Postgres/Redis:

| Variable | Typical Docker Maven value | Host default |
|----------|----------------------------|--------------|
| `IT_POSTGRES_HOST` | `postgres` | `localhost` |
| `IT_POSTGRES_PORT` | `5432` | `5432` |
| `IT_POSTGRES_DB` | isolated DB e.g. `aptech4_catalog_it` | same |
| `IT_POSTGRES_USER` / `IT_POSTGRES_PASSWORD` | compose credentials | from `.env` |
| `IT_REDIS_HOST` / `IT_REDIS_PORT` | `redis` / `6379` | `localhost` / `6379` |

Create isolated DBs on the shared Postgres volume (do **not** wipe `aptech4_pgdata`):

```bash
docker exec aptech4-postgres psql -U aptech -d aptech4 -c "CREATE DATABASE aptech4_catalog_it;"
```

Example full suite (Maven on `aptech4_net`):

```bash
docker run --rm --network aptech4_net \
  -v "$PWD/backend:/app" -v aptech4_m2:/root/.m2 -w /app \
  -e IT_POSTGRES_HOST=postgres -e IT_REDIS_HOST=redis \
  -e IT_POSTGRES_DB=aptech4_catalog_it \
  maven:3.9.9-eclipse-temurin-21 ./mvnw test
```

Shared helper: `com.aptech.aptech4.support.IntegrationTestSupport`.

## Integration tests — Testcontainers sample (P07-T01)

`TestcontainersBootstrapTest` starts ephemeral `postgres:16-alpine` + `redis:7-alpine`, applies Liquibase, checks JDBC + Redis + `GET /api/v1/health`.

Requires Docker Engine reachable by Testcontainers **1.21.4+** (Engine 29 needs API ≥ 1.44; see `src/test/resources/docker-java.properties`). Prefer host JDK 21 when available.

From a Maven container, mount the host socket:

```bash
docker run --rm \
  -v "$PWD/backend:/app" -v aptech4_m2:/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  --add-host=host.docker.internal:host-gateway \
  -w /app \
  maven:3.9.9-eclipse-temurin-21 \
  ./mvnw -Dtest=TestcontainersBootstrapTest test
```

If Docker is unavailable, JUnit skips the class (`disabledWithoutDocker=true`). Nested Testcontainers on Docker Desktop can still flake (B-001); Compose `IT_*` remains the verified path for the full suite.

## Notes

- Hibernate `ddl-auto=validate`; schema changes only via Liquibase.
- Do not reset shared Docker volumes for ordinary test runs.
