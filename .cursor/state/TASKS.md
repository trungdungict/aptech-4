# TASKS — Authoritative registry

Statuses: `TODO` | `IN_PROGRESS` | `BLOCKED` | `IN_REVIEW` | `DONE` | `DEFERRED`  
Do not renumber IDs. Do not delete completed tasks.

Counts are maintained in `PROGRESS.md` (recalculate from this file).

---

## P00 — Assessment and planning

### P00-T01 — Repository and toolchain assessment
- **Phase:** P00
- **Status:** DONE
- **Deps:** —
- **Files:** (read-only) repo root, `.gitignore`, `README.md`, `LICENSE`
- **AC:** Path verified; Git status recorded; tools detected; empty vs partial documented.
- **Verify:** `ls`, `git status`, version commands for Java/Maven/Node/Flutter/Docker/Make
- **Evidence:** 2026-10-28 — Repo `/Users/Martin/Documents/APTECT_PROJECT_4/aptech-4` on `main` @ `a8b9a10`, clean WT; only README/LICENSE/gitignore. Present: Node v26.5.1, npm 12.0.2, Docker 29.6.2, Compose v5.3.1, Make 3.81, Homebrew. **Missing:** JDK, Maven, Flutter, gh, pnpm/yarn.
- **Notes:** Preserve all existing files.

### P00-T02 — Create `.cursor/` control structure
- **Phase:** P00
- **Status:** DONE
- **Deps:** P00-T01
- **Files:** `.cursor/rules/*`, `plans/`, `state/`, `decisions/`, `PROJECT_PLAYBOOK.md`
- **AC:** Required tree exists with valid `.mdc` frontmatter; playbook has resume checklists.
- **Verify:** `find .cursor -type f | sort`
- **Evidence:** 2026-10-28 — Structure and files created in first planning session.
- **Notes:** —

### P00-T03 — Master roadmap and phase plans
- **Phase:** P00
- **Status:** DONE
- **Deps:** P00-T02
- **Files:** `.cursor/plans/00-master-plan.md` … `08-integration-final-qa.md`
- **AC:** Phases P00–P17 mapped; objectives/ACs/verification per plan.
- **Verify:** Plans readable and cross-linked from playbook.
- **Evidence:** 2026-10-28 — Nine plan files written.
- **Notes:** P02/P14 share `06-docker-makefile-local.md` by design.

### P00-T04 — Initialize task registry and state files
- **Phase:** P00
- **Status:** DONE
- **Deps:** P00-T03
- **Files:** `TASKS.md`, `PROGRESS.md`, `CURRENT_SESSION.md`, `BLOCKERS.md`
- **AC:** All implementation tasks registered with stable IDs; statuses valid.
- **Verify:** Grep statuses; spot-check dependency references.
- **Evidence:** 2026-10-28 — Registry initialized this session.
- **Notes:** —

### P00-T05 — Cursor rules + ADR template + ADR-001
- **Phase:** P00
- **Status:** DONE
- **Deps:** P00-T02
- **Files:** `.cursor/rules/*.mdc`, `decisions/ADR-000-template.md`, `ADR-001-modular-monolith.md`
- **AC:** Governance + resume alwaysApply; domain rules scoped; ADR template usable.
- **Verify:** Frontmatter present on all `.mdc` files.
- **Evidence:** 2026-10-28 — Six rules + template + ADR-001.
- **Notes:** —

### P00-T06 — Consistency check of planning docs
- **Phase:** P00
- **Status:** DONE
- **Deps:** P00-T04, P00-T05
- **Files:** playbook, plans, state, rules
- **AC:** Resume prompt matches; next task points to first actionable P01 item; no orphan refs.
- **Verify:** Manual cross-read + `find .cursor -type f`
- **Evidence:** 2026-10-28 — Cross-checked during session; next = P01-T01.
- **Notes:** —

---

## P01 — Foundation

### P01-T01 — Expand `.gitignore` for full stack
- **Phase:** P01
- **Status:** DONE
- **Deps:** P00-T06
- **Files:** `.gitignore`
- **AC:** Ignores Java/Maven, Node, Flutter, IDE, `.env`, OS junk, build outputs; keeps `.env.example` trackable.
- **Verify:** `git check-ignore -v .env backend/target node_modules` (patterns exist)
- **Evidence:** 2026-10-28 — Expanded .gitignore; `git check-ignore -v` matches `.env`, `backend/target/...`, `node_modules/`, `dist/`; `.env.example` remainable.

- **Notes:** —

### P01-T02 — Create directory tree placeholders
- **Phase:** P01
- **Status:** DONE
- **Deps:** P01-T01
- **Files:** `backend/`, `frontend/`, `mobile/`, `deploy/nginx/`, `deploy/ec2/`, `docs/`, `.github/workflows/`
- **AC:** Directories exist with `.gitkeep` or README stubs where empty.
- **Verify:** `find . -maxdepth 3 -type d`
- **Evidence:** 2026-10-28 — Created backend/frontend/mobile/deploy/{nginx,ec2}/docs/scripts/.github/workflows with .gitkeep/README stubs.

- **Notes:** —

### P01-T03 — Root README rewrite
- **Phase:** P01
- **Status:** DONE
- **Deps:** P01-T02
- **Files:** `README.md`
- **AC:** Purpose, structure, tool requirements, link to playbook + resume prompt section.
- **Verify:** File contains `.cursor/PROJECT_PLAYBOOK.md`
- **Evidence:** 2026-10-28 — README rewritten with structure, prerequisites, playbook link, resume prompt.

- **Notes:** —

### P01-T04 — `.env.example`
- **Phase:** P01
- **Status:** DONE
- **Deps:** P01-T02
- **Files:** `.env.example`
- **AC:** Documents Postgres, Redis, JWT, API, Vite, Flutter-related vars; placeholders only.
- **Verify:** `grep -E 'PASSWORD|SECRET|KEY' .env.example` shows placeholders not real secrets
- **Evidence:** 2026-10-28 — `.env.example` added with placeholder secrets only; real `.env` gitignored and created locally via make env-check.

- **Notes:** —

### P01-T05 — `docs/TECH_STACK.md` version pins
- **Phase:** P01
- **Status:** DONE
- **Deps:** P01-T02
- **Files:** `docs/TECH_STACK.md`
- **AC:** Lists intended versions (Java 21, Boot 3.x, Node, Flutter, Postgres, Redis, etc.).
- **Verify:** File exists and names pinned intents.
- **Evidence:** 2026-10-28 — `docs/TECH_STACK.md` written; later updated for Boot 4.1.2 + Compose image tags.

- **Notes:** —

### P01-T06 — Root `AGENTS.md`
- **Phase:** P01
- **Status:** DONE
- **Deps:** P00-T06
- **Files:** `AGENTS.md`
- **AC:** Short pointer to playbook + state; no full roadmap duplicate.
- **Verify:** File references `.cursor/PROJECT_PLAYBOOK.md` and `TASKS.md`
- **Evidence:** 2026-10-28 — Root `AGENTS.md` points to playbook/state without duplicating roadmap.

- **Notes:** —

### P01-T07 — ADR-002 environment configuration
- **Phase:** P01
- **Status:** DONE
- **Deps:** P01-T04
- **Files:** `.cursor/decisions/ADR-002-env-configuration.md`
- **AC:** Decision on `.env` + Compose + client URL config recorded.
- **Verify:** ADR file present with Context/Decision/Consequences.
- **Evidence:** 2026-10-28 — ADR-002-env-configuration.md accepted.

- **Notes:** —

---

## P02 — Compose + Makefile foundation

### P02-T01 — `docker-compose.yml` Postgres + Redis
- **Phase:** P02
- **Status:** DONE
- **Deps:** P01-T04
- **Files:** `docker-compose.yml`
- **AC:** Services with healthchecks; named volumes; ports from env; `docker compose config` OK.
- **Verify:** `docker compose config`
- **Evidence:** 2026-10-28 — `docker-compose.yml` with postgres:16-alpine + redis:7-alpine, healthchecks, named volumes; `docker compose config` OK.

- **Notes:** Pinned to floating minor tags because patch tags failed to pull timely on host.

### P02-T02 — Makefile deps targets
- **Phase:** P02
- **Status:** DONE
- **Deps:** P02-T01
- **Files:** `Makefile`
- **AC:** `help`, `up-deps`, `down-deps`, `ps`, `logs-deps` (or equivalent) work; down preserves volumes.
- **Verify:** `make help`; `make up-deps`; health; `make down-deps`
- **Evidence:** 2026-10-28 — Root Makefile: help, env-check, up-deps, down-deps, ps, logs-deps, health-deps, reset-deps-volumes; `make help` OK.

- **Notes:** —

### P02-T03 — Verify deps stack healthy
- **Phase:** P02
- **Status:** DONE
- **Deps:** P02-T02
- **Files:** Makefile, compose
- **AC:** Postgres and Redis healthchecks pass; evidence recorded.
- **Verify:** `docker compose ps`; redis-cli/psql ping
- **Evidence:** 2026-10-28 — `make up-deps` → healthy; `pg_isready` accepting; `redis-cli ping` → PONG; `make down-deps` left volumes `aptech4_pgdata` + `aptech4_redisdata`.

- **Notes:** Docker Desktop was started during session after initial daemon-down failure.

---

## P03 — Liquibase + domain architecture

### P03-T01 — Spring Boot Maven Wrapper scaffold
- **Phase:** P03
- **Status:** DONE
- **Deps:** P02-T03, P01-T05
- **Files:** `backend/pom.xml`, `mvnw*`, `src/main/java/**`
- **AC:** App starts against Compose Postgres (or documented Docker build path if no local JDK).
- **Verify:** `./mvnw -v` or Docker Maven; `./mvnw -q -DskipTests package`
- **Evidence:** 2026-10-28 — Spring Initializr Boot **4.1.2** / Java 21 scaffold in `backend/` (ADR-003). Built via `docker run maven:3.9.9-eclipse-temurin-21 ./mvnw -DskipTests package` → `backend/target/aptech4-api-0.0.1-SNAPSHOT.jar`. Smoke: jar on `aptech4_net` → `GET /api/v1/health` HTTP 200 `{"status":"OK","service":"aptech4-api"}`; Liquibase baseline applied.

- **Notes:** Host JDK still missing (B-001); Docker Temurin is the verified build path. ContextLoads test not run (needs Testcontainers/P07).

### P03-T02 — Liquibase baseline changelogs
- **Phase:** P03
- **Status:** DONE
- **Deps:** P03-T01
- **Files:** `backend/src/main/resources/db/changelog/**`
- **AC:** Fresh migrate creates core tables.
- **Verify:** App start or liquibase update against empty DB; `\dt`
- **Evidence:** 2026-10-28 — Added ordered changelogs `010`–`070` + ADR-004. Verified on **isolated** Postgres container `aptech4-pg-p03t02` / DB `aptech4_migrate` (did not reset shared `aptech4_pgdata`). Docker Maven `./mvnw -DskipTests package` OK; app Started; Liquibase **14** changesets EXECUTED (Run:14 Previously:0). `\dt` tables: addresses, cart_items, carts, categories, inventory, order_items, orders, payments, products, refresh_tokens, roles, user_roles, users (+ liquibase tables). Confirmed: `password_hash`, `token_hash`, `uq_cart_items_cart_product`, order_items `product_name`/`unit_price` snapshots, roles CUSTOMER/ADMIN. Isolated volume removed after verify; shared volumes preserved.

- **Notes:** Schema-only (no demo seed). Shared Compose volumes not dropped.

### P03-T03 — Domain packages + core entities
- **Phase:** P03
- **Status:** DONE
- **Deps:** P03-T02
- **Files:** `backend/src/main/java/**/domain/**`
- **AC:** Entities for User/Role/Product/Category/Cart/Order match schema; OOP layering documented.
- **Verify:** Compile + mapping smoke test
- **Evidence:** 2026-10-28 — Entities under identity/catalog/cart/order (+ AbstractUuidEntity); `backend/docs/DOMAIN_LAYERING.md`. Docker Maven: `UserAccountSafetyTest,OrderStatusTest` green; `-DskipTests package` OK. Isolated DB `aptech4_entities` on `aptech4-pg-p03t03`: Liquibase 14 changesets + Hibernate `ddl-auto=validate` → **Started Aptech4ApiApplication**. CHAR/`bpchar` mapped via `@JdbcTypeCode(SqlTypes.CHAR)`. Shared volumes preserved; isolated volume removed after verify.

- **Notes:** UserAccount/ShopOrder names avoid JDK/JPA clashes. No schema changes. Sensitive hashes excluded from toString + @JsonIgnore.

### P03-T04 — OpenAPI baseline
- **Phase:** P03
- **Status:** DONE
- **Deps:** P03-T01
- **Files:** OpenAPI config / springdoc
- **AC:** `/v3/api-docs` or swagger UI reachable in local profile.
- **Verify:** curl api-docs after boot
- **Evidence:** 2026-10-28 — springdoc-openapi-starter-webmvc-ui **3.2.0** (Boot 4.1.2). OpenApiConfig + Bearer `bearer-jwt`; Security permits `/v3/api-docs/**`, `/swagger-ui/**` only (other `/api/v1/**` still 401). `paths-to-match=/api/v1/**`, `show-actuator=false`; `application-prod.yml` disables docs. Docs: `backend/docs/OPENAPI.md`. Tests: OpenApiConfigTest + OpenApiSecurityWebTest (+ prior unit tests) green via Docker Maven. Isolated DB smoke: `/v3/api-docs` HTTP 200 title/version/paths=`/api/v1/health`/bearer-jwt; Swagger UI 200; health 200; protected 401. Shared volumes preserved.

- **Notes:** No business controllers added. ddl-auto remains validate. No Liquibase changes.

### P03-T05 — Health endpoint
- **Phase:** P03
- **Status:** DONE
- **Deps:** P03-T01
- **Files:** actuator or custom `/api/v1/health`
- **AC:** Health returns OK when DB up.
- **Verify:** `curl` health
- **Evidence:** 2026-10-28 — `HealthController` `/api/v1/health` + actuator health permitted in bootstrap `SecurityConfig`; curl evidence under P03-T01.

- **Notes:** Full JWT security still P04.

---

## P04 — Auth JWT RBAC

### P04-T01 — Security dependencies + JWT config
- **Phase:** P04
- **Status:** DONE
- **Deps:** P03-T03
- **Files:** security config, JWT props
- **AC:** Security filter chain boots; public vs protected routes defined.
- **Verify:** Context loads; unauthorized → 401
- **Evidence:** 2026-10-28 — JJWT **0.13.0** + ADR-005 (HS256, separate access/refresh secrets, `token_use`, issuer). JwtProperties fail-fast (min 32, secrets must differ); CORS explicit origins (no `*`+credentials); CSRF off for Bearer; httpBasic removed; JwtAuthenticationFilter + 401/403 JSON handlers; `ROLE_` mapping. Docs: `backend/docs/SECURITY.md`. Docker Maven tests green: JwtServiceTest, JwtProperties*, JwtSecurityWebTest (401/403/admin200/CORS/refresh-rejected), OpenApiSecurityWebTest, prior unit tests. Isolated smoke: health 200, `/v3/api-docs` 200, protected/bad-bearer 401; missing JWT secrets → startup fail (NOSECRET_FAILFAST_OK). Shared volumes preserved.

- **Notes:** Login/register/refresh HTTP APIs deferred to P04-T02/T03. Test-only SecurityProbeController lives under src/test only.

### P04-T02 — Register + login + access token
- **Phase:** P04
- **Status:** DONE
- **Deps:** P04-T01
- **Files:** `AuthController`/`AuthService`, DTOs, `PasswordEncoderConfig`, `AccountUserDetails*`, repos, `AuthIntegrationTest`, `SECURITY.md`
- **AC:** Customer can register/login; receives access JWT.
- **Verify:** Integration test + curl
- **Evidence:** 2026-10-28 — Docker Maven on `aptech4_net` vs isolated DB `aptech4_auth_it`: `./mvnw test` → **38 tests, 0 failures** (1 skipped app context). Curl smoke `:18081` on `aptech4_smoke_p04t02`: register **201** (CUSTOMER, normalized email, access JWT), dup **409**, login **200**, bad login **401** generic message, anon protected **401** / bearer **404** after `/error` permit, OpenAPI paths present. Shared volumes `aptech4_pgdata`/`aptech4_redisdata`/`aptech4_m2` preserved; smoke DB dropped.
- **Notes:** Register returns access token (no refresh — P04-T03). Auth IT uses Compose Postgres/Redis (`IT_*` env); Testcontainers DinD skipped on Docker Desktop. Client cannot choose role (`RegisterRequest` has no role field; always CUSTOMER from seeded Liquibase roles).

### P04-T03 — Refresh token lifecycle
- **Phase:** P04
- **Status:** DONE
- **Deps:** P04-T02
- **Files:** `AuthService` refresh/logout, `RefreshTokenRepository`, `RefreshTokenHasher`, Auth DTOs, `SECURITY.md`/`OPENAPI.md`, Auth IT
- **AC:** Refresh rotates/revokes per ADR; reuse detection policy documented.
- **Verify:** Security/integration tests
- **Evidence:** 2026-10-28 — Docker Maven `./mvnw test package` on Compose `aptech4_auth_it` → **FULL_BUILD_TEST_OK**. Auth IT covers hash-only store, rotation, old-token reject, reuse→revoke-all (REQUIRES_NEW), concurrent refresh **200/401**, logout **204** then refresh **401**, access≠refresh Bearer. Curl smoke `:18082` DB `aptech4_smoke_p04t03` → **P04_T03_SMOKE_OK**. Shared volumes preserved; smoke DB dropped.
- **Notes:** Logout revokes **one** refresh session only; access JWT remains valid until `exp` (no denylist). Reuse of revoked refresh revokes all active refresh tokens for that user.

### P04-T04 — RBAC admin vs customer
- **Phase:** P04
- **Status:** DONE
- **Deps:** P04-T02
- **Files:** `SecurityConfig` `/api/v1/admin/**`, `SecurityRoles`, test probes, `RbacSecurityWebTest`, `SECURITY.md`
- **AC:** Admin routes require `ADMIN`; customer cannot access.
- **Verify:** Security tests both roles
- **Evidence:** 2026-10-28 — Docker Maven `./mvnw test package` → **FULL_BUILD_TEST_OK**. `RbacSecurityWebTest` 9/9 (401/403/200, ownership, admin≠customer). Auth IT: register `role=ADMIN` ignored → CUSTOMER + admin path 403. Curl smoke `:18083`: anon admin **401**, customer **403**, minted ADMIN JWT **404** (matcher passed, no prod controller). Volumes preserved.
- **Notes:** No production admin CRUD yet (P05+). ADMIN does not bypass customer ownership. No default admin credentials.

---

## P05 — Catalog APIs

### P05-T01 — Category public + admin APIs
- **Phase:** P05
- **Status:** DONE
- **Deps:** P04-T04, P03-T03
- **Files:** `catalog` api/application/persistence, Liquibase `080-categories-active`, Security public GET categories
- **AC:** List categories; admin CRUD.
- **Verify:** API tests
- **Evidence:** 2026-10-28 — Liquibase **15** changesets (`active` + `uq_categories_name_lower`). Docker Maven `./mvnw test package` → **FULL_BUILD_TEST_OK**. Category IT: public active-only, admin 401/403/CRUD, duplicate 409, status soft-disable, DELETE **405**. Curl smoke `:18084` → **P05_T01_SMOKE_OK**. Shared volumes preserved.
- **Notes:** No hard delete; deactivate via PATCH status. Inactive hidden from public.

### P05-T02 — Product list/search/pagination
- **Phase:** P05
- **Status:** DONE
- **Deps:** P05-T01
- **Files:** `ProductController`/`ProductService`/`ProductRepository`, `PageResponse`, Liquibase `090-products-public-query-indexes`, Security public GET products
- **AC:** Search + page/size/sort; empty/invalid handled.
- **Verify:** API tests
- **Evidence:** 2026-10-28 — Liquibase **16** changesets. Public `GET /api/v1/products` (+`/{id}`): zero-based page, default size 20 / max 100 (reject), sort whitelist `name|price|createdAt` + id tie-breaker; filters `q`, `categoryId` (active only else 404), `minPrice`/`maxPrice`, `inStock`. Inactive product/category hidden. Docker Maven `./mvnw test package` → **67 tests, 0 failures, 1 skipped** → **FULL_BUILD_TEST_OK**. Curl smoke `:18085` / `aptech4_smoke_p05t02` → **P05_T02_SMOKE_OK**. Shared volumes preserved; smoke DB dropped.
- **Notes:** Public discovery only; admin product CRUD deferred to P05-T03. `imageUrl` always null (no media column).

### P05-T03 — Product admin CRUD
- **Phase:** P05
- **Status:** DONE
- **Deps:** P05-T02, P04-T04
- **Files:** `AdminProductController`, create/update/status DTOs, `ProductService` admin methods, OpenAPI/SECURITY/DOMAIN_LAYERING
- **AC:** Admin create/update/delete; validation errors clear.
- **Verify:** API + security tests
- **Evidence:** 2026-10-28 — Admin `/api/v1/admin/products` GET/POST/PUT + PATCH status; soft-disable only (DELETE **405**). No inventory create/update on product APIs. Category must be active on create/update; SKU conflict **409**. Docker Maven `./mvnw test package` → **69 tests, 0 failures, 1 skipped** → **FULL_BUILD_TEST_OK**. Curl smoke `:18086` / `aptech4_smoke_p05t03` → **P05_T03_SMOKE_OK** (401/403/CRUD/deactivate→public 404/dup 409). Shared volumes preserved; smoke DB dropped.
- **Notes:** Soft-disable via status PATCH (no hard DELETE). Inventory reserved for **P06-T02** after cart **P06-T01**.

---

## P06 — Cart, inventory, checkout, orders

### P06-T01 — Cart APIs for authenticated customer
- **Phase:** P06
- **Status:** DONE
- **Deps:** P05-T02, P04-T02
- **Files:** `CartController`/`CartService`/repos/DTOs, `CurrentUser`, OpenAPI/SECURITY/DOMAIN_LAYERING
- **AC:** Add/update/remove lines; ownership enforced.
- **Verify:** Integration tests
- **Evidence:** 2026-10-28 — `/api/v1/cart` CUSTOMER-only; owner from JWT `sub`; merge qty on unique `(cart_id,product_id)`; server `BigDecimal` pricing; inactive lines `available=false`; no inventory mutation; foreign item **404**, ADMIN **403**, anon **401**. Docker Maven `./mvnw test package` → **71 tests, 0 failures, 1 skipped** → **FULL_BUILD_TEST_OK**. Curl smoke `:18087` / `aptech4_smoke_p06t01` → **P06_T01_SMOKE_OK**. Shared volumes preserved; smoke DB dropped.
- **Notes:** Inventory reservation deferred to **P06-T02**. Clear-all via `DELETE /api/v1/cart/items`.

### P06-T02 — Inventory reservation strategy
- **Phase:** P06
- **Status:** DONE
- **Deps:** P06-T01
- **Files:** ADR-006, Liquibase `100-inventory-reservation`, `InventoryReservationService`, HTTP reserve/release/get, expiry job
- **AC:** Concurrent-safe stock decrement; ADR recorded.
- **Verify:** Concurrent test or documented locking approach + test
- **Evidence:** 2026-10-28 — ADR-006: `available=on_hand-reserved`; statuses ACTIVE→RELEASED|CONFIRMED; TTL 900s; atomic conditional UPDATEs; confirm **service-only** for P06-T03. Liquibase **19** changesets. Docker Maven → **73 tests, 0 failures, 1 skipped** → **FULL_BUILD_TEST_OK**. Concurrent IT: **5/10 success + 5 conflict** on stock=5 (no oversell). Curl smoke `:18088` / `aptech4_smoke_p06t02` → **P06_T02_SMOKE_OK**. Shared volumes preserved.
- **Notes:** Cart does not reserve. Confirm not exposed as customer HTTP payment shortcut.

### P06-T03 — Checkout + mock payment
- **Phase:** P06
- **Status:** DONE
- **Deps:** P06-T02
- **Files:** ADR-007, Liquibase `110`/`111`, `CheckoutService`/`CheckoutController`, `MockPaymentGateway`
- **AC:** Successful mock pay creates order; failure path leaves stock consistent.
- **Verify:** Integration tests
- **Evidence:** 2026-10-28 — `POST /api/v1/checkout`: reserve→mock pay→confirm/release; server BigDecimal snapshots; idempotency + locks; cart clear only on PAID. Liquibase **21** changesets. Docker Maven → **75 tests, 0 failures, 1 skipped** → **FULL_BUILD_TEST_OK**. Concurrent checkout: **1 PAID + 1 conflict** on stock=1. Curl smoke `:18089` / `aptech4_smoke_p06t03` → **P06_T03_SMOKE_OK**. Shared volumes preserved.
- **Notes:** Mock payment only (not a real gateway). Order list/status → P06-T04.

### P06-T04 — Order lifecycle APIs
- **Phase:** P06
- **Status:** DONE
- **Deps:** P06-T03
- **Files:** ADR-008, `OrderService`/`OrderController`/`AdminOrderController`, status transitions
- **AC:** Customer lists own orders; admin updates status.
- **Verify:** API + security tests
- **Evidence:** 2026-10-28 — Customer `GET /orders` (+`/{id}`), cancel `PENDING_PAYMENT` only; Admin list/detail + `PATCH .../status` whitelist (ADR-008). Snapshots unchanged after catalog rename. Ownership **404**; RBAC 401/403. Concurrent status: **1 success + 1 conflict**. Docker Maven → **79 tests, 0 failures, 1 skipped** → **FULL_BUILD_TEST_OK**. Curl smoke `:18090` / `aptech4_smoke_p06t04` → **P06_T04_SMOKE_OK**. Shared volumes preserved.
- **Notes:** Cancel after PAID not allowed for customer; admin cancel is status-only (no refund/restock).

---

## P07 — Backend tests

### P07-T01 — Testcontainers setup
- **Phase:** P07
- **Status:** DONE
- **Deps:** P03-T01, P02-T03
- **Files:** ADR-009, `IntegrationTestSupport`, `TestcontainersBootstrapIT`, `docs/TESTING.md`, TC **1.21.4**
- **AC:** Sample IT boots with Postgres (and Redis if needed).
- **Verify:** One IT green
- **Evidence:** 2026-10-28 — Host OpenJDK 21 + Testcontainers **1.21.4** (`api.version=1.44`): `TestcontainersBootstrapIT` → **1/0/0** (ephemeral Postgres 16 + Redis 7, Liquibase 21, JDBC/Redis/health). Full `./mvnw test` vs Compose `aptech4_catalog_it` → **80 tests, 0 failures, 0 skipped** → **FULL_BUILD_TEST_OK**. Nested Maven-in-Docker TC still flaky (B-001); Compose `IT_*` remains suite path. Shared volumes preserved.
- **Notes:** Dual-mode ADR-009. Engine 29 needs TC ≥1.21.4.

### P07-T02 — Auth security test suite
- **Phase:** P07
- **Status:** DONE
- **Deps:** P04-T04, P07-T01
- **Files:** `AuthSecurityRegressionIT` (TC), `JwtSecurityWebTest`/`JwtServiceTest` gaps, TESTING.md map
- **AC:** Covers anonymous/customer/admin matrix for critical routes.
- **Verify:** `./mvnw -Dtest=...Security... test`
- **Evidence:** 2026-10-28 — Security-focused run **57/0/0** (`AuthSecurityRegressionIT` **5** via Testcontainers PG16+Redis7; `JwtSecurityWebTest` 12; `RbacSecurityWebTest` 9; `AuthIntegrationTest` 15 Compose; unit JWT/CORS fail-fast). Full `./mvnw test` → **86/0/0** → **FULL_BUILD_TEST_OK**. Concurrent refresh still covered by AuthIT (200+401). No production security policy weakened. Shared volumes preserved.
- **Notes:** Gap-fill suite; existing AuthIT/Rbac retained. Log-output assertions not claimed (filter only logs class name at DEBUG).

### P07-T03 — Catalog/cart/order test coverage gate
- **Phase:** P07
- **Status:** DONE
- **Deps:** P06-T04, P07-T01
- **Files:** coverage matrix in TESTING.md, JaCoCo 0.8.13, `InventoryAvailableTest`, ProductIT default size/createdAt, rename `*IT`→`*Test` for Surefire
- **AC:** Full `./mvnw test` green; failures fixed not deleted.
- **Verify:** `./mvnw test` / `./mvnw verify`
- **Evidence:** 2026-10-28 — Matrix closed (catalog/cart/inventory/checkout/order). Gap-fill: default page size 20 + `createdAt` sort; ADR-006 available unit. Renamed `AuthSecurityRegressionTest` / `TestcontainersBootstrapTest` (Surefire omits bare `*IT`). Host `./mvnw clean verify` → **92/0/0**; JaCoCo LINE **~82.6%**, BRANCH **~63%**; check **LINE ≥ 70%** met. Concurrent reserve/checkout remain Compose PG ITs. Shared volumes preserved.
- **Notes:** Quantitative gate is verify-phase floor, not a vanity %; invariant ITs remain the quality bar.

---

## P08 — Demo seed / reset

### P08-T01 — Deterministic seed data
- **Phase:** P08
- **Status:** DONE
- **Deps:** P06-T04
- **Files:** ADR-010, `com.aptech.aptech4.seed.*`, Makefile `seed-check`/`seed-demo`, `docs/DEMO_SEED.md`
- **AC:** Fixed demo users/products; documented credentials in `.env.example` style.
- **Verify:** seed + login + list products
- **Evidence:** 2026-10-28 — Explicit `APTECH_SEED_ENABLED` one-shot (not Liquibase). Idempotent upserts by email/slug/SKU; BCrypt passwords; prod profile refused. `make seed-demo` ×2 → first +2/+4/+12, second +0/~2/~4/~12; 12 `DEMO-*` products. TC `DemoSeedIntegrationTest` + `DemoSeedGuardTest`. `./mvnw clean verify` → **95/0/0**, JaCoCo gate met. Shared volumes preserved.
- **Notes:** Re-seed does not overwrite existing passwords (reset → P08-T02).

### P08-T02 — Safe reset workflow
- **Phase:** P08
- **Status:** DONE
- **Deps:** P08-T01, P02-T02
- **Files:** ADR-011, `DemoResetService`/`DemoResetRunner`, Makefile `reset-demo`/`reset-demo-dry`/`down`/`clean`, `docs/DEMO_SEED.md`
- **AC:** Explicit reset recreates baseline; normal `down` does not wipe volumes.
- **Verify:** dry-run + confirm gates; TC reset preserves non-demo; reseed restores catalog
- **Evidence:** 2026-10-28 — Scoped delete by exact demo emails/slugs/SKUs (no truncate/volume wipe). Preflight: prod refused; `CONFIRM=yes` required; dry-run plan-only. TC `DemoResetIntegrationTest` + `DemoResetServiceGuardTest` on disposable PG. `make -n clean`/`down` keep volumes. `./mvnw clean verify` → **101/0/0**, JaCoCo LINE ~82.8%. No shared-volume destructive evidence run.
- **Notes:** `reset-demo` = delete demo scope + reseed; volume wipe remains `reset-deps-volumes` only.

---

## P09 — React customer storefront

### P09-T01 — Vite React TS scaffold + Tailwind/shadcn baseline
- **Phase:** P09
- **Status:** DONE
- **Deps:** P01-T04
- **Files:** `frontend/**`, ADR-012, `frontend/README.md`
- **AC:** App runs; strict TS; Vitest smoke.
- **Verify:** `npm test`; `npm run build`
- **Evidence:** 2026-10-28 — Vite 8 + React 19 + TS strict; Tailwind v4 (`@tailwindcss/vite`) + shadcn-ready `components.json`/`cn()`; Router + QueryClient providers; RHF+Zod deps; `VITE_API_BASE_URL` via `.env.example`. Host Node 26.5.1. `npm run typecheck` pass; `npm test` **1/1**; `npm run lint` (oxlint) pass; `npm run build` pass. No backend/Compose changes.
- **Notes:** Prefer Node 22 LTS if a future Vite release rejects Node 26.

### P09-T02 — API client + auth pages
- **Phase:** P09
- **Status:** DONE
- **Deps:** P09-T01, P04-T03
- **Files:** `lib/api/*`, `features/auth/**`, ADR-013, `frontend/README.md`
- **AC:** Login/register against API; token storage strategy safe for SPA.
- **Verify:** Component tests + manual/API mock
- **Evidence:** 2026-10-28 — Contract: `POST /api/v1/auth/{register,login,refresh,logout}` with JSON access+refresh; register 201 auto-session CUSTOMER-only; logout 204. Access token memory-only; refresh in sessionStorage (ADR-013). Vitest **15/15**; typecheck/lint/build pass. Live API smoke **NOT RUN** (localhost:8080 down). No backend changes.
- **Notes:** XSS trade-off documented for client-held refresh tokens (no HttpOnly cookies in ADR-005).

### P09-T03 — Catalog browse + detail
- **Phase:** P09
- **Status:** DONE
- **Deps:** P09-T02, P05-T02
- **Files:** `features/catalog/**`, router, README
- **AC:** List/search/detail wired to API.
- **Verify:** Vitest + build
- **Evidence:** 2026-10-28 — Public `GET /api/v1/products` (+`/{id}`) and `GET /api/v1/categories`. Filters `q`/`categoryId`/`minPrice`/`maxPrice`/`inStock`/`page`/`size`/`sort` synced to URL; debounce search; VND formatting; `imageUrl` null → placeholder. Vitest **27/27**; typecheck/lint/build pass. Live API smoke **NOT RUN** (8080 down). No backend changes.
- **Notes:** No Add to Cart (P09-T04).

### P09-T04 — Cart + checkout + orders UI
- **Phase:** P09
- **Status:** DONE
- **Deps:** P09-T03, P06-T04
- **Files:** cart/checkout/orders + minimal `/api/v1/addresses`
- **AC:** Customer can complete mock purchase flow in UI.
- **Verify:** Vitest critical paths; manual smoke against local API
- **Evidence:** `npm run typecheck` pass; `npm test` **42/42**; `npm run lint -- --deny-warnings` pass; `npm run build` pass; backend `./mvnw -DskipTests compile` SUCCESS; live API smoke **NOT RUN** (`localhost:8080` down)
- **Notes:** Minimal Address REST (`GET`/`POST /api/v1/addresses`) added for checkout `addressId`. Idempotency via body `idempotencyKey` (stable per attempt). Mock payment honesty in UI. Private Query cache cleared on logout (`AuthQueryBridge`).

---

## P10 — React admin

### P10-T01 — Admin shell + role gate
- **Phase:** P10
- **Status:** DONE
- **Deps:** P09-T02, P04-T04
- **Files:** admin routes/layout, RequireAdmin, roles helper
- **AC:** Non-admin redirected/forbidden.
- **Verify:** RTL tests
- **Evidence:** `npm run typecheck` pass; `npm test` **53/53**; `npm run lint -- --deny-warnings` pass; `npm run build` pass; live API smoke **NOT RUN**; no backend auth DTO changes
- **Notes:** Role from AuthResponse.user.roles (login/refresh); UX gate only — backend /admin/** remains authority. Fail-closed for empty/unknown roles.

### P10-T02 — Admin catalog CRUD UI
- **Phase:** P10
- **Status:** DONE
- **Deps:** P10-T01, P05-T03
- **Files:** admin products/categories
- **AC:** CRUD works against API.
- **Verify:** Vitest + manual smoke
- **Evidence:** `npm run typecheck` pass; `npm test` **65/65**; `npm run lint -- --deny-warnings` pass; `npm run build` pass; live API smoke **NOT RUN**; no backend changes
- **Notes:** Soft-disable via PATCH …/status; no hard delete; no inventory fields on product forms. Category edit loads from admin list (no GET-by-id).

### P10-T03 — Admin orders UI
- **Phase:** P10
- **Status:** DONE
- **Deps:** P10-T01, P06-T04
- **Files:** admin orders
- **AC:** List + status update.
- **Verify:** Vitest + smoke
- **Evidence:** `npm run typecheck` pass; `npm test` **75/75**; `npm run lint -- --deny-warnings` pass; `npm run build` pass; live API smoke **NOT RUN**; no backend changes
- **Notes:** `/api/v1/admin/orders` list/detail + PATCH status (ADR-008). No email in DTO — show userId. Cannot set PAID/FAILED.

---

## P11 — Flutter customer app

### P11-T01 — Flutter project scaffold
- **Phase:** P11
- **Status:** DONE
- **Deps:** P01-T02
- **Files:** `mobile/**`
- **AC:** App analyzes; placeholder home.
- **Verify:** `flutter analyze`; `flutter test`
- **Evidence:** 2026-10-28 Docker toolchain `aptech4-mobile-toolchain:3.47.8`. `make mobile-build-image` 0; versions Flutter 3.47.8 / Dart 3.13.7 / Shorebird 1.6.133 / JDK 17; doctor Android SDK 36.0.0 ✓; `mobile-create` → analyze/test 0; debug APK built; `mobile-shorebird-auth` (`shorebird account` OK); `shorebird init` → `shorebird.yaml`. No release/patch publish.
- **Notes:** Host Flutter still absent; Docker path is authoritative.

### P11-T02 — Dio + auth + secure storage
- **Phase:** P11
- **Status:** DONE
- **Deps:** P11-T01, P04-T03
- **Files:** auth/data layer
- **AC:** Login stores tokens securely; refresh path defined.
- **Verify:** Unit tests
- **Evidence:** 2026-10-28 — `make mobile-pub-get` 0; `mobile-analyze` 0; `mobile-test` **15/15**; `mobile-build-android` 0. Contract: POST `/api/v1/auth/{register,login,refresh,logout}` matching backend + React client. Live API smoke NOT RUN.
- **Notes:** Access token memory-only; refresh in FlutterSecureStorage; single-flight refresh; no JWT decode for authz.

### P11-T03 — Catalog/cart/checkout screens
- **Phase:** P11
- **Status:** DONE
- **Deps:** P11-T02, P06-T04
- **Files:** `mobile/lib/features/{catalog,cart,checkout,auth/presentation}`, `mobile/lib/app/*`
- **AC:** Parity with customer web for core purchase flow.
- **Verify:** Widget/unit tests + Docker analyze/test/APK
- **Evidence:** 2026-10-28 — `make mobile-analyze` 0; `mobile-test` **33/33** (incl. prior 15 auth); `mobile-build-android` → `app-debug.apk`. Contracts: GET `/products`, `/products/{id}`, `/categories`; CUSTOMER `/cart`, `/checkout`, `/addresses`. Live API smoke NOT RUN.
- **Notes:** Server-side cart (JWT ownership). Checkout uses idempotencyKey+addressId; no local payment success. GoRouter gates `/cart|/checkout|/orders`.

---

## P12 — Flutter API URL + APK

### P12-T01 — Build-time API base URL
- **Phase:** P12
- **Status:** DONE
- **Deps:** P11-T01
- **Files:** `mobile/lib/core/config/api_config.dart`, Android debug/profile manifests, `mobile/README.md`, ADR-014
- **AC:** `--dart-define=API_BASE_URL=` documented; ADR if needed.
- **Verify:** Code reads define; docs cover emulator/device/EC2
- **Evidence:** 2026-10-29 — `ApiConfig` + Dio single base; cleartext HTTP **debug/profile only**; docs for emulator/LAN/Compose/HTTPS; ADR-014. Live API connectivity **NOT RUN**.
- **Notes:** ADR-002 + ADR-014 (not ADR-005)

### P12-T02 — Makefile APK target
- **Phase:** P12
- **Status:** DONE
- **Deps:** P12-T01, P11-T03
- **Files:** `Makefile`, `scripts/mobile-docker.sh`
- **AC:** `make apk API_BASE_URL=...` produces APK; path in evidence.
- **Verify:** Run make apk; `ls` artifact
- **Evidence:** 2026-10-29 — Debug APK + release APK + release AAB via Docker. Paths: `…/flutter-apk/app-debug.apk` (~159MB), `…/app-release.apk` (~52MB), `…/bundle/release/app-release.aab` (~51MB). Alias `mobile-build-android`; `mobile-build-release-aab` added. Shorebird publish **not** run (`CONFIRM` gate verified). Device/emulator E2E **deferred** (after EC2).
- **Notes:** Docker only; APK/AAB gitignored; debug signing for release artifacts (no prod keystore)

---

## P13 — Redis caching

### P13-T01 — Cache product/category reads
- **Phase:** P13
- **Status:** DONE
- **Deps:** P05-T02, P02-T03
- **Files:** `CacheConfig`, `catalog/cache/*`, `ProductService`/`CategoryService` `@Cacheable`, `application.yml`
- **AC:** Repeated reads hit Redis; TTL configured.
- **Verify:** IT or manual redis-cli monitor evidence
- **Evidence:** 2026-10-29 — Host `./mvnw verify` **111/0/0**; JaCoCo check met. `CatalogCacheIntegrationTest` (TC PG+Redis): miss then hit (0 SQL on hit); list key isolation; ADR-015. TTL categories **120s** / products **60s**. Live redis-cli MONITOR not required (IT evidence).
- **Notes:** Public reads only; admin/cart/checkout not cached.

### P13-T02 — Cache invalidation on admin writes
- **Phase:** P13
- **Status:** DONE
- **Deps:** P13-T01, P05-T03
- **Files:** `CatalogCacheInvalidator`, product/category mutations, inventory reserve/confirm/release
- **AC:** Update/delete clears relevant keys; ADR if non-obvious.
- **Verify:** IT
- **Evidence:** 2026-10-29 — IT: product/category soft-disable clears caches → public **404** / omitted from list. Product-list region cleared wholesale (combinatorial keys). Inventory mutations also flush product caches. CacheErrorHandler swallows Redis get/put/evict errors → DB remains SoT.
- **Notes:** ADR-015 (invalidation trade-off documented)

---

## P14 — Local prod-like deployment

### P14-T01 — Compose services for API + web (+ nginx optional)
- **Phase:** P14
- **Status:** DONE
- **Deps:** P03-T05, P09-T01, P02-T01
- **Files:** `docker-compose.yml` (profile `stack`), `backend/Dockerfile`, `frontend/Dockerfile`, `deploy/nginx/local-stack.conf`, ADR-016
- **AC:** Full stack up without AWS creds.
- **Verify:** `docker compose --profile stack config`; `make build-stack`; `make deploy-local`; `make health-local`; `make smoke-local`
- **Evidence:** 2026-10-29 — api+web+postgres+redis healthy; web `:8088` → `/api/v1/health` + `/api/v1/categories` 200; prod bundle has `/api/v1` and **no** `localhost:8080`; PG marker survived stop/start (volumes kept)
- **Notes:** Same-origin nginx proxy; API loopback `127.0.0.1:8080`; seed not auto-run

### P14-T02 — Makefile local deploy/health/logs
- **Phase:** P14
- **Status:** DONE
- **Deps:** P14-T01
- **Files:** Makefile
- **AC:** deploy-local, health-local, logs-local verified.
- **Verify:** Execute targets; record output
- **Evidence:** 2026-10-29 — `build-stack`, `deploy-local`, `health-local`, `smoke-local`, `down`/`down-local` (no `-v`) verified
- **Notes:** Also `logs-local`, `ps` (profile stack)

### P14-T03 — Wire seed/reset into local demo
- **Phase:** P14
- **Status:** DONE
- **Deps:** P14-T02, P08-T02
- **Files:** Makefile (`seed-*`/`reset-*`/`smoke-demo`/`invalidate-catalog-cache`), `docs/DEMO_SEED.md`, README/deploy notes
- **AC:** Demo rehearsal possible via Make alone.
- **Verify:** seed → health → sample curl
- **Evidence:** 2026-10-29 — `seed-check` dry counts; `seed-demo`×2 idempotent (+0/~2/~4/~12); `reset-demo-dry` no mutation; `reset-demo` refuses without `CONFIRM=yes`; confirmed reset preserved `NONDEMO-P14-MARKER`; reseed +12; `smoke-demo` login+DEMO SKUs via `:8088`; `health-local`/`smoke-local` OK; `./mvnw verify` **111/0/0**. `SEED_SERVER_PORT=18081` coexists with stack API; `APTECH_SEED_ENABLED=false` in api container
- **Notes:** Reused P08 ADR-010/011; no volume wipe; catalog Redis keys cleared after seed/reset

---

## P15 — EC2

### P15-T01 — Nginx + remote compose overlays
- **Phase:** P15
- **Status:** TODO
- **Deps:** P14-T02
- **Files:** `deploy/nginx/**`, `deploy/ec2/**`
- **AC:** Config serves API/web on :80; DB/Redis not published publicly.
- **Verify:** Config file review + `nginx -t` in container if possible
- **Evidence:** —
- **Notes:** —

### P15-T02 — Makefile EC2 targets (no localhost default)
- **Phase:** P15
- **Status:** TODO
- **Deps:** P15-T01
- **Files:** Makefile
- **AC:** Missing `EC2_HOST` fails clearly; `-n` dry-run OK.
- **Verify:** `make -n deploy-ec2`; failure without host
- **Evidence:** —
- **Notes:** —

### P15-T03 — Remote deploy smoke (approval-gated)
- **Phase:** P15
- **Status:** TODO
- **Deps:** P15-T02
- **Files:** deploy scripts
- **AC:** With approved host, health via public IPv4 HTTP evidenced OR explicitly BLOCKED awaiting host.
- **Verify:** `curl http://$EC2_HOST/...`
- **Evidence:** —
- **Notes:** Do not run without user approval + host config.

### P15-T04 — EC2 HTTP security documentation
- **Phase:** P15
- **Status:** TODO
- **Deps:** P15-T01
- **Files:** `docs/EC2_SECURITY.md`
- **AC:** States HTTP demo risks; SG recommendations; not production-secure.
- **Verify:** Doc exists and clear
- **Evidence:** —
- **Notes:** —

---

## P16 — CI + smoke + docs

### P16-T01 — GitHub Actions CI workflow
- **Phase:** P16
- **Status:** TODO
- **Deps:** P07-T03, P09-T01
- **Files:** `.github/workflows/ci.yml`
- **AC:** Runs backend + frontend checks on PR/push.
- **Verify:** Workflow YAML valid; push evidence when possible
- **Evidence:** —
- **Notes:** —

### P16-T02 — Local smoke script/Make target
- **Phase:** P16
- **Status:** TODO
- **Deps:** P14-T03
- **Files:** `scripts/smoke.sh` or Make
- **AC:** Smoke hits health + auth + catalog happy path.
- **Verify:** Run against local stack
- **Evidence:** —
- **Notes:** Related tooling already available: `make postman-smoke` (Newman folder 00-Smoke) + `make smoke-local` / `smoke-demo`. P16-T02 may wrap/unify these.

### P16-T03 — Operator / demo documentation pack
- **Phase:** P16
- **Status:** TODO
- **Deps:** P14-T03
- **Files:** `docs/**`
- **AC:** Runbook covers local, demo, EC2, APK, resume.
- **Verify:** Docs review checklist
- **Evidence:** —
- **Notes:** —

---

## P18 — Promotion domain and database foundation (ADR-020)

### P18-T01 — Confirm ADR-020 Accepted + R1–R9
- **Phase:** P18
- **Status:** DONE
- **Deps:** —
- **Files:** `.cursor/decisions/ADR-020-promotion-management.md`, `.cursor/plans/09-promotion-management.md`
- **AC:** ADR Accepted; R1–R9 locked
- **Evidence:** 2026-10-31 — ADR-020 Accepted with R1–R9

### P18-T02 — Liquibase 140–144
- **Phase:** P18
- **Status:** DONE
- **Deps:** P18-T01
- **Files:** `backend/src/main/resources/db/changelog/changes/140-*.yaml` … `144-*.yaml`, master include
- **AC:** Additive only; ≤130 untouched
- **Evidence:** 2026-10-31 — IT DB applied 140–144 (29 changesets); PromotionDomainIntegrationTest green

### P18-T03 — Promotion JPA entities/repos
- **Phase:** P18
- **Status:** DONE
- **Deps:** P18-T02
- **Files:** `backend/.../promotion/domain/*`, `promotion/persistence/*`
- **Evidence:** 2026-10-31 — domain unit + IT green

### P18-T04 — Order discount + snapshot entity
- **Phase:** P18
- **Status:** DONE
- **Deps:** P18-T02
- **Files:** `ShopOrder`, `OrderPromotionSnapshot`, migration 144
- **Evidence:** 2026-10-31 — IT asserts default discount 0 + snapshot persist; CheckoutIntegrationTest still green

### P18-T05 — Shared upload purpose PRODUCT|PROMOTION
- **Phase:** P18
- **Status:** DONE
- **Deps:** P18-T02
- **Files:** `UploadPurpose`, `ImageUploadPathRules`, upload service/controller/security/web config
- **Evidence:** 2026-10-31 — ProductImageUploadServiceTest 17/17; path isolation tests green

---

## P19 — Promotion Admin and Public APIs (ADR-020)

### P19-T01 — Admin Promotion Strategy CRUD
- **Phase:** P19
- **Status:** DONE
- **Deps:** P18
- **Files:** `AdminPromotionStrategyController`, `PromotionStrategyService`, strategy DTOs
- **AC:** ADMIN-only; validation; OpenAPI paths
- **Evidence:** 2026-10-31 — PromotionApiIntegrationTest

### P19-T02 — Admin Promotion Campaign CRUD + lifecycle
- **Phase:** P19
- **Status:** DONE
- **Deps:** P19-T01
- **Files:** `AdminPromotionCampaignController`, `PromotionCampaignService`
- **AC:** activate/pause/schedule/preview; UTC effectiveStatus; R1/R4
- **Evidence:** 2026-10-31 — lifecycle + schedule ITs

### P19-T03 — Campaign eligibility management
- **Phase:** P19
- **Status:** DONE
- **Deps:** P19-T02
- **Files:** `PUT …/eligibility`, product/category validation by strategy type
- **Evidence:** 2026-10-31 — eligibility IT

### P19-T04 — Admin Promotion Placement CRUD
- **Phase:** P19
- **Status:** DONE
- **Deps:** P19-T02
- **Files:** `AdminPromotionPlacementController`, `PromotionPlacementService`
- **Evidence:** 2026-10-31 — placement create/order ITs

### P19-T05 — Public homepage + placements APIs
- **Phase:** P19
- **Status:** DONE
- **Deps:** P19-T04
- **Files:** `PublicPromotionController`, `PublicPromotionService`, SecurityConfig permit GET
- **AC:** status ∧ UTC window ∧ ordering; empty → empty JSON (FoodMart FE fallback P20)
- **Evidence:** 2026-10-31 — public filter/order/empty ITs

### P19-T06 — Preview endpoint (schedule effectiveness only)
- **Phase:** P19
- **Status:** DONE
- **Deps:** P19-T02
- **Files:** `POST …/preview` — no checkout pricing (P22)
- **Evidence:** 2026-10-31 — preview IT

### P19-T07 — OpenAPI / docs
- **Phase:** P19
- **Status:** DONE
- **Deps:** P19-T05
- **Files:** `backend/docs/OPENAPI.md`
- **Evidence:** 2026-10-31 — Promotions section added

### P19-T08 — Public placement cache + invalidation (R7)
- **Phase:** P19
- **Status:** DONE
- **Deps:** P19-T05
- **Files:** `PromotionCache*`, `CacheConfig`, `PromotionCacheInvalidator` on mutations
- **AC:** short TTL + miss after Admin mutation without waiting for TTL
- **Evidence:** 2026-10-31 — PromotionCacheIntegrationTest

---

## P20 — Storefront homepage promotion UI (ADR-020 R3)

### P20-T01 — Public promotion DTOs + API client
- **Phase:** P20
- **Status:** DONE
- **Deps:** P19-T05
- **Files:** `frontend/src/features/promotions/types.ts`, `api/promotions-api.ts`, query keys
- **Evidence:** 2026-10-31 — promotions-api tests

### P20-T02 — HomeHeroCarousel + HomePromoStack + PromoBannerCard
- **Phase:** P20
- **Status:** DONE
- **Deps:** P20-T01
- **Files:** `features/promotions/components/*`
- **AC:** left hero carousel + right stacked promos; image/CTA resolution
- **Evidence:** 2026-10-31 — HomePage promotion tests

### P20-T03 — FoodMart fallback on empty/error/null slots
- **Phase:** P20
- **Status:** DONE
- **Deps:** P20-T02
- **Files:** `lib/foodmart-fallback.ts`, `HomePromotionShowcase.tsx`
- **AC:** R3 — empty/fail/null promoTop|promoBottom → static FoodMart
- **Evidence:** 2026-10-31 — fallback unit + HomePage ITs

### P20-T04 — Wire HomePage + Vitest coverage
- **Phase:** P20
- **Status:** DONE
- **Deps:** P20-T03
- **Files:** `pages/HomePage.tsx`, `pages/HomePage.test.tsx`, promotion lib tests
- **Evidence:** 2026-10-31 — frontend tests + production build

---

## P21 — Admin promotion UI (ADR-020)

### P21-T01 — Admin promotions API client + routes/RBAC
- **Phase:** P21
- **Status:** DONE
- **Deps:** P19, P20
- **Files:** `admin/promotions/api/*`, `router.tsx`, `nav.ts`, lazy routes
- **Evidence:** 2026-10-31 — CUSTOMER blocked; Admin hub deep-link

### P21-T02 — Strategy list/create/edit/status
- **Phase:** P21
- **Status:** DONE
- **Deps:** P21-T01
- **Files:** `AdminStrategyListPage`, `AdminStrategyFormPage`
- **Evidence:** 2026-10-31 — strategy create IT

### P21-T03 — Campaign list + edit tabs (details/lifecycle/eligibility/placements/preview)
- **Phase:** P21
- **Status:** DONE
- **Deps:** P21-T02
- **Files:** `AdminCampaignListPage`, `AdminCampaignEditPage`, UTC helpers, promotion upload field
- **AC:** activate/pause/schedule/preview; eligibility; placement CRUD + purpose=PROMOTION; confirm destructive actions
- **Evidence:** 2026-10-31 — AdminPromotionPages tests

### P21-T04 — Vitest + build; no P20 regression
- **Phase:** P21
- **Status:** DONE
- **Deps:** P21-T03
- **Evidence:** 2026-10-31 — admin promotions + storefront promotion tests + `npm run build`

---

## P22 — Checkout pricing, snapshots, expiry (ADR-020)

**Phase status:** DONE — P22-T01…T06 complete (2026-10-31).

**Audit notes:** Schema `144` present. Checkout applies `PromotionPricingService` before payment; snapshots cascade with order. P22 clarifications: order-level dual eligibility = OR; aggregate cap via end-trim so snapshot sums == `discount_amount`. No Flutter. Inventory semantics unchanged.

### P22-T01 — PromotionPricingService + unit matrix
- **Phase:** P22
- **Status:** DONE
- **Deps:** P18, P19
- **Files:** `promotion/application/PromotionPricingService.java`, `PricingCartLine`, `AppliedPromotion`, `PromotionPricingResult`, `PromotionPricingServiceTest`
- **AC:**
  - Backend-only selection; ignore any client campaign preference
  - Effective via `PromotionCampaign.isPricingEffective` (ACTIVE ∧ inclusive UTC ∧ strategy ACTIVE ∧ ≠ BANNER_ONLY)
  - Eligibility: order-level empty=all; one set=that set; both=OR (P22); PRODUCT/CATEGORY type-specific; `min_*` vs **full cart**
  - Priority DESC, starts_at ASC, id ASC; non-stackable first + all stackable; non-compounding; end-trim so snapshots sum to order discount; HALF_UP scale 2
  - Empty cart / zero subtotal / ineligible → discount 0
- **Verify:** Unit tests AC-S1…S9 + OR eligibility + caps/rounding/UTC bounds with fixed `Clock`
- **Evidence:** 2026-10-31 — `./mvnw -Dtest=PromotionPricingServiceTest test` → Tests run: 21, Failures: 0

### P22-T02 — Wire CheckoutService + persist snapshots + CheckoutResponse
- **Phase:** P22
- **Status:** DONE
- **Deps:** P22-T01
- **Files:** `CheckoutService`, `CheckoutResponse`, `PromotionCampaignRepository.findPricingWindowCandidates`, `CheckoutPromotionIntegrationTest`
- **AC:**
  - After line/subtotal build, before payment: apply pricing; set `discount_amount`, `promotion_summary`, snapshots; `totalAmount = subtotal + shipping − discount` (≥ 0)
  - Reserve / confirm / release / payment / cart-clear / idempotency unchanged
  - Exception before commit → no order, no snapshots; FAILED payment still persists order+snapshots atomically
  - No-promotion path: `discount_amount = 0`, empty snapshots, legacy total
  - Response: `discountAmount` + `appliedPromotions` (ADR D7)
- **Verify:** Focused checkout ITs (no promo + fixed/% + stack + eligibility + inactive windows + failure + client fields ignored)
- **Evidence:** 2026-10-31 — `./mvnw -Dtest=PromotionPricingServiceTest,CheckoutPromotionIntegrationTest,CheckoutIntegrationTest test` → Tests run: 30, Failures: 0

### P22-T03 — Order GET exposes discount + applied promotions
- **Phase:** P22
- **Status:** DONE
- **Deps:** P22-T02
- **Files:** `OrderResponse`, `OrderService`, `OrderPromotionGetIntegrationTest`
- **AC:** Read from persisted order/snapshots only; legacy orders → `discountAmount=0`, empty list; never reprice from live campaigns; ownership unchanged
- **Verify:** Order detail IT with snapshot after campaign/strategy delete still shows historical amounts
- **Evidence:** 2026-10-31 — `./mvnw -Dtest=OrderPromotionGetIntegrationTest,OrderLifecycleIntegrationTest,CheckoutPromotionIntegrationTest,CheckoutIntegrationTest,CustomerDeterministicAssistantServiceTest test` → Tests run: 29, Failures: 0

### P22-T04 — Minimal FE checkout/order discount display
- **Phase:** P22
- **Status:** DONE
- **Deps:** P22-T02, P22-T03
- **Files:** `checkout/types.ts`, `orders/types.ts`, `OrderPriceSummary`, customer/admin order detail + history, focused Vitest
- **AC:** Types match API; show order-level discount when > 0; applied promo names/amounts; no client-side discount math; no campaign picker; legacy zero discount renders normally
- **Verify:** Focused Vitest + `npm run build`
- **Evidence:** 2026-10-31 — focused tests 28 passed; `npm run build` SUCCESS
- **Notes:** Scope = contract consumption only (user constraint + ADR D7)

### P22-T05 — CampaignExpiryJob
- **Phase:** P22
- **Status:** DONE
- **Deps:** P19-T02
- **Files:** `CampaignExpiryJob`, `PromotionCampaignService.expireDueCampaigns`, `PromotionCampaignRepository.findActivePastEnd`, `application.yml` `aptech.promotions.expiry-poll-ms`, `CampaignExpiryIntegrationTest`
- **AC:** ACTIVE ∧ `clock.instant() > ends_at` → EXPIRED; inclusive end remains ACTIVE; PAUSED/SCHEDULED unchanged; idempotent; UTC `Clock`; snapshots untouched; checkout independent of job
- **Verify:** IT with fixed `Clock`; re-run no-op
- **Evidence:** 2026-10-31 — `./mvnw -Dtest=CampaignExpiryIntegrationTest,PromotionCampaignEffectivenessTest,PromotionPricingServiceTest test` → Tests run: 26, Failures: 0
- **Notes:** No new Liquibase — `idx_promotion_campaigns_status_window` already exists

### P22-T06 — Integration matrix + suite green
- **Phase:** P22
- **Status:** DONE
- **Deps:** P22-T02…T05
- **AC:** Cover prompt test list 1–15 (no promo, fixed, %, priority, stackable/non, product/category, inactive/paused/not-started/expired, UTC bounds, cap/negative guard, rounding, snapshot persist, delete FK SET NULL, rollback, existing checkout/inventory green, expiry idempotency)
- **Verify:** Full relevant backend IT suite; report commands + counts
- **Evidence:** 2026-10-31 — Matrix gaps closed (category checkout, end-trim+summary, two-stackable IT, AC-H6 strategy mutate, DRAFT expiry, expiry→cache clear). `make test-promotions` / focused `-Dtest=…` → **55 tests, 0 failures**. Full `./mvnw test` promotion classes green; AuthIntegrationTest needed local `CREATE DATABASE aptech4_auth_it` (env gap, not promo). No production code changes.
- **Notes:** `PromotionCacheIntegrationTest` (Testcontainers) green in full suite when Docker available; excluded from `make test-promotions` default list comment.

---

## P23 — Promotion E2E / smoke (Phase 7)

### P23-T01 — Promotion journey smoke via local stack
- **Phase:** P23
- **Status:** DONE
- **Deps:** P22-T06, P20, P21
- **Files:** `scripts/smoke-promotions.sh`, `Makefile` (`smoke-promotions`)
- **AC:**
  - Admin auth + create/configure campaign (banner + %); invalid strategy rejected
  - Public homepage shows active hero; hides draft/paused/past-window; FoodMart fallback path intact when empty
  - Customer checkout applies server discount + snapshots; order GET preserves history after campaign mutate
  - Past `endsAt` effectiveness without wall-clock wait; no real payments; cleanup smoke-owned rows only
- **Verify:** `make smoke-promotions` against `WEB_PORT` nginx stack + `DEMO_*` credentials; optional browser UI check; focused FE Vitest if touched
- **Evidence:** 2026-10-31 — `make smoke-promotions` **OK** (all PASS asserts). Browser: Featured offers showed live hero title. Focused FE Vitest **28/28**. ACTIVE→EXPIRED job status flip **BLOCKED** on live stack (no test Clock) — covered by `CampaignExpiryIntegrationTest`. No production behavior changes.
- **Notes:** Reuses existing bash/nginx smoke pattern (no new Playwright/Cypress). Updated in P24 to check `demo-promo-*` seed fixtures.

---

## P24 — Promotion mock / seed data (Phase 8)

### P24-T01 — Idempotent demo promotion fixtures
- **Phase:** P24
- **Status:** DONE
- **Deps:** P23-T01, P08-T01, P18
- **Files:** `DemoPromotionCatalog`, `DemoPromotionSeedService`, `DemoSeedService`/`DemoSeedResult`/`DemoSeedMarkers`, `DemoResetService`/`Plan`/`Result`, `PromotionCampaignRepository.findBySlug*`, `docs/DEMO_SEED.md`, `scripts/smoke-promotions.sh`
- **AC:**
  - Seed creates strategies/campaigns/placements for % / fixed / product / category / OR / stackable / scheduled / paused / draft / expired / hero
  - Idempotent upsert by `DEMO-PROMO-*` codes and `demo-promo-*` slugs; reset deletes only those keys
  - Pricing campaigns PAUSED by default; hero ACTIVE; no seeded promo orders
  - `make seed-demo` ×2 stable; smoke reports missing seed and validates fixtures when present
- **Verify:** `DemoSeedIntegrationTest` + `DemoResetIntegrationTest`; `make seed-demo` ×2; `make smoke-promotions`
- **Evidence:** 2026-10-31 — seed/reset ITs **9/0**; seed-demo first `strat +9 camp +12`, second `+0/~9/~12`; smoke-promotions **OK** (seed hero PASS). No `.env`/volume wipe. Phase 8 complete.
- **Notes:** Activate paused pricing campaigns in Admin for live discount demos. Natural keys later migrated to `PROMO-*` / grocery slugs (see UX cleanup below).

### UX — Promotion page image, strategy pagination, realistic seed (ad-hoc)
- **Phase:** post-P24
- **Status:** DONE
- **Deps:** P24-T01
- **Files:** `DemoPromotionCatalog`/`DemoPromotionSeedService`/`DemoSeedMarkers`/`DemoResetService`, `promo-hero.jpg`, `Makefile` seed sync, `HomeHeroCarousel`, `AdminStrategyListPage`, `smoke-promotions.sh`, FE/BE tests, `docs/DEMO_SEED.md`
- **AC:**
  - Featured offers renders real campaign JPG (not 1×1 PNG); safe URL + `onError` fallback; Makefile syncs into uploads volume
  - Admin strategies paginated client-side (size 10) over existing List API; page clamp after invalid page
  - Visible seed names/codes realistic (no Demo/Smoke); retired `DEMO-PROMO-*`/`demo-promo-*` purged on seed/reset; pricing PAUSED; hero ACTIVE; smoke uses new stable IDs
- **Verify:** FE Vitest (HomeHero + Admin promo pages); seed/reset ITs; promotion ITs; `npm run build`; `make seed-demo` ×2; `make smoke-promotions`; live homepage image HTTP 200
- **Evidence:** 2026-10-31 — FE **15/15** focused; seed/reset ITs green; PromotionApi/Domain/Expiry ITs green (after creating missing `aptech4_catalog_it`); FE production build OK; seed1 purged 12/9 retired then `+9/+12`; seed2 `+0/~9/~12` + hero JPG sync; smoke-promotions **OK**; homepage title `Ưu đãi mua sắm tiết kiệm`, JPG **184781** bytes; Admin strategies no Demo/Smoke labels.
- **Notes:** Pagination is client-side because admin strategies API remains an unpaginated List. Orphaned prior `SMK-*`/`TMP-*` smoke strategies cleaned by exact code prefix (no volume wipe).

### UX — Admin auth redirect on expired/missing session (ad-hoc)
- **Phase:** post-P24
- **Status:** DONE
- **Deps:** existing auth client + RequireAdmin
- **Files:** `session-coordinator.ts`, `AuthProvider.tsx`, `api-client.ts`, `safe-redirect.ts`, auth/API Vitest
- **AC:**
  - Protected 401 after failed refresh clears session and redirects to login with safe `next=`
  - Missing storage → `/admin/orders` goes to login (no 401 Retry UI)
  - 403 keeps session; 5xx does not clear; refresh single-flight preserved
- **Verify:** focused Vitest auth/api/admin + `npm run build`
- **Evidence:** 2026-10-31 — Vitest **45/45**; production build OK.

### UX — Admin Edit Product current image preview (ad-hoc)
- **Phase:** post-P24
- **Status:** DONE
- **Deps:** product admin form + `resolveProductImage`
- **Files:** `ProductImageUploadField.tsx`, `AdminProductFormPage.tsx`, product image/form Vitest
- **AC:** Current image (upload path or catalog fallback) labeled; no-image placeholder; new-file preview; empty file input preserves `imageUrl` on save
- **Verify:** focused product Vitest + `npm run build`
- **Evidence:** 2026-10-31 — Vitest **34/34**; production build OK.

### UX — Persist FoodMart seed product images (ad-hoc)
- **Phase:** post-P24
- **Status:** DONE
- **Deps:** FoodMart seed + product upload path rules
- **Files:** `FoodMartProductImages.java`, `db/demo/foodmart-product-images.json`, `db/demo/product-images/*`, `DemoSeedService.java`, Makefile `seed-demo` sync, `DEMO_SEED.md`, seed ITs
- **AC:** Every seed-owned `FOOD-*` product persists non-null `/uploads/products/{uuid}.ext`; assets synced into uploads volume; re-seed idempotent and repairs missing files; non-seed products untouched
- **Verify:** `FoodMartProductImagesTest` + seed/reset ITs; FE product image Vitest; `npm run build`; `make seed-demo` ×2; public catalog + image HTTP; `make smoke-promotions`
- **Evidence:** 2026-11-01 — BE tests **7/7** (`FoodMartProductImagesTest` 2, `FoodMartCatalogTest` 1, `DemoSeedIntegrationTest` 1, `DemoResetIntegrationTest` 3); FE **34/34**; build OK; seed×2 → 48/48 non-null unique URLs, sample HTTP 200; smoke-promotions OK

### UX — Shared Admin/Customer list pagination (ad-hoc)
- **Phase:** post-P24
- **Status:** DONE
- **Deps:** existing list pages + `PageResponse`
- **Files:** `components/pagination/*`, `CatalogPagination.tsx`, Admin/Customer list pages, `PRODUCT_DEFAULT_PAGE_SIZE` / `ORDER_DEFAULT_PAGE_SIZE`
- **AC:** Shared First/Prev/numbers/ellipsis/Next/Last; default size 10; hide when ≤10 records; server vs client strategy per page; filter/sort reset + clamp; no double-pagination
- **Verify:** pagination unit tests + modified list page Vitest + full FE suite + `npm run build`
- **Evidence:** 2026-11-01 — focused **67/67**; full FE **233/233** (48 files); production build OK

### UX — Product card Add to Cart with auth guard (ad-hoc)
- **Phase:** post-P24
- **Status:** DONE
- **Deps:** cart API + Product Detail ATC + `safeInternalPath`
- **Files:** `ProductCard.tsx`, `clamp-quantity.ts`, Product Detail shared clamp, ProductCard/Detail/List Vitest
- **AC:** Card qty + ATC for customers; guest ATC → login with safe `next`; no detail navigation; OOS disables; reuse cart mutation; detail ATC unchanged
- **Verify:** focused card/catalog/cart/auth Vitest + full FE suite + `npm run build`
- **Evidence:** 2026-11-01 — focused **34/34**; full FE **242/242** (49 files); production build OK

### UX — Aptech Assistant launcher vs pagination overlap (ad-hoc)
- **Phase:** post-P24
- **Status:** DONE
- **Deps:** ChatPanel + shared Pagination
- **Files:** `ChatPanel.tsx`, `ChatPanelFallback.tsx`, `chat-launcher.ts`, `useChatLauncherClearance.ts`, `Pagination.tsx`, `index.css`
- **AC:** Compact/minimizable FAB; pagination safe-area via CSS vars; reopen from minimized; no pagination logic changes
- **Verify:** focused chat + pagination Vitest + full FE suite + `npm run build`
- **Evidence:** 2026-11-01 — focused chat+pagination **46/46**; full FE **247/247** (50 files); production build OK

---

## P17 — Final acceptance

### P17-T01 — End-to-end acceptance checklist execution
- **Phase:** P17
- **Status:** TODO
- **Deps:** P16-T02, P09-T04, P10-T03
- **Files:** `docs/ACCEPTANCE.md`
- **AC:** Checklist filled with dates/commands/evidence links.
- **Verify:** Manual execution recorded
- **Evidence:** —
- **Notes:** Flutter/EC2 items may remain BLOCKED with sign-off.

### P17-T02 — Graduation demo script
- **Phase:** P17
- **Status:** TODO
- **Deps:** P17-T01, P08-T01
- **Files:** `docs/DEMO_SCRIPT.md`
- **AC:** Step-by-step customer + admin demo ≤15 minutes.
- **Verify:** Rehearsal notes
- **Evidence:** —
- **Notes:** —

### P17-T03 — Final playbook/state hygiene
- **Phase:** P17
- **Status:** TODO
- **Deps:** P17-T02
- **Files:** `.cursor/state/*`
- **AC:** All critical tasks DONE/DEFERRED/BLOCKED explained; resume prompt accurate.
- **Verify:** Progress counts match TASKS.md
- **Evidence:** —
- **Notes:** —

---

## Supersessions / deferred log

_(none yet)_
