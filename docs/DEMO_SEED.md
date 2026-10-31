# Demo seed & reset (P08)

Deterministic, idempotent fixtures for **local / school demos only**. Schema stays in Liquibase; seed/reset are separate one-shot Spring runs.

See ADR-010 (seed) and ADR-011 (reset).

## Workflow comparison

| Command | What it does | Destructive? |
|---------|----------------|--------------|
| `make seed-check` | Prints DB target; refuses `prod` profile; checks DEMO_/JWT secrets | No |
| `make seed-demo` | Upsert demo users + FoodMart catalog/inventory; purge retired `DEMO-*` keys | Deletes **retired catalog keys only** |
| `make reset-demo-dry` | Prints JDBC URL + counts of matching demo rows | No |
| `make reset-demo CONFIRM=yes` | Deletes **demo-scoped** rows, then reseeds | Yes — demo scope only |
| `make clean` | `mvn clean` (build artifacts) | No DB/volumes |
| `make down` / `make down-deps` | Stop Postgres/Redis containers | Volumes **kept** |
| `make reset-deps-volumes` | `compose down --volumes` after typing `yes` | Full volume wipe (separate) |

**`reset-demo` is not a database reset.** It does not drop schema, truncate all tables, or remove `aptech4_pgdata`.

## What seed creates

| Kind | Natural key | Notes |
|------|-------------|-------|
| Roles | `CUSTOMER`, `ADMIN` | From Liquibase `010-identity` — seed looks them up; **reset never deletes roles** |
| Admin user | `DEMO_ADMIN_EMAIL` (default `admin@aptech4.local`) | Role `ADMIN`; password hashed with BCrypt |
| Customer user | `DEMO_CUSTOMER_EMAIL` (default `customer@aptech4.local`) | Role `CUSTOMER` |
| Categories | FoodMart slugs from `db/demo/foodmart-catalog.json` (`food-*`) | 8 grocery categories |
| Products | FoodMart SKUs (`FOOD-*`) | 48 products; includes zero-stock SKUs |
| Inventory | per seeded product | `on_hand ≥ reserved`; never negative |
| Journeys | addresses, cart, sample orders, notifications | FoodMart products only |

Seed also **purges retired** legacy `DEMO-*` / `demo-*` natural keys listed in `DemoSeedMarkers` (local-demo guard). It does **not** delete unrelated non-demo rows.

## Reset scope

Deletes only rows matching:

1. Configured demo emails (`DEMO_ADMIN_EMAIL` / `DEMO_CUSTOMER_EMAIL`) + journey extras
2. Category slugs from `DemoSeedMarkers.resetCategorySlugs()` (FoodMart + retired)
3. Product SKUs from `DemoSeedMarkers.resetProductSkus()` (FoodMart + retired)
4. Dependents of those rows (carts, reservations, orders/payments/checkout for those users/products, inventory, `user_roles`, addresses, refresh tokens)

Non-demo users/products/categories and role reference data are kept. FK constraints stay enabled (no `TRUNCATE` of business tables).

### Preflight / refusal

Reset (and seed) refuse when:

- Spring profile `prod` is active
- JDBC URL cannot be resolved
- Destructive path without `aptech.seed.reset-confirm=yes` (Makefile: missing `CONFIRM=yes`)
- Both `aptech.seed.enabled` and `aptech.seed.reset` are true

Dry-run does **not** require confirmation.

### Recovery if reset fails mid-flight

Destructive reset runs in a **single Spring `@Transactional`**. On error the transaction rolls back; demo and non-demo data remain as before the attempt. Re-run `make reset-demo-dry`, then `make reset-demo CONFIRM=yes` after fixing the cause. If you interrupted outside that transaction, prefer dry-run + reseed rather than guessing.

## Credentials (demo-only)

Defined in [`.env.example`](../.env.example). Copy to gitignored `.env` for local use. **Never use in production.**

| Account | Email | Password (from `.env.example`) | Role | Intended use |
|---------|-------|--------------------------------|------|--------------|
| CUSTOMER | `customer@aptech4.local` | `customer` (local default) | CUSTOMER | Catalog / cart / checkout / orders |
| ADMIN | `admin@aptech4.local` | `admin` (local default) | ADMIN | `/admin` dashboard, products, categories, orders |

See also [DEMO_ENVIRONMENT.md](./DEMO_ENVIRONMENT.md) for FoodMart catalog, `make up-all` / `make down-all`, and expected counts.

Verified locally against Compose stack (`http://localhost:8088`) after `make seed-demo` (login 200 + role checks; CUSTOMER rejected on `/api/v1/admin/**`).

```
DEMO_ADMIN_EMAIL=admin@aptech4.local
DEMO_ADMIN_PASSWORD=admin
DEMO_CUSTOMER_EMAIL=customer@aptech4.local
DEMO_CUSTOMER_PASSWORD=customer
```

Re-seed alone does **not** reset an existing user's password; use `make reset-demo CONFIRM=yes` (or `make down-all CONFIRM=yes && make up-all`) to recreate demo users with env passwords.

### Compose web CORS note

Browser calls from `http://localhost:8088` send an `Origin` header on JSON POSTs. The `api` service Compose config always allows `http://localhost:8088` and `http://127.0.0.1:8088` (plus Vite origins). A host `.env` that only listed `:5173`/`:3000` previously caused **403 Invalid CORS request** on `/register` — fixed by literal stack CORS in `docker-compose.yml`.

## How to run

Works against **Compose Postgres** (`POSTGRES_HOST=localhost` on the published port). Compatible with the full local stack (`make deploy-local`): one-shot seed/reset bind `SEED_SERVER_PORT` (default **18081**) so they do not collide with the long-running API on `127.0.0.1:8080`.

`make deploy-local` / API container keep `APTECH_SEED_ENABLED=false` — seed never auto-runs on stack start.

```bash
cp -n .env.example .env   # if needed; set DEMO_* and JWT_* secrets
make up-deps              # or make deploy-local for api+web too
make seed-check           # dry preflight + read-only demo counts (no writes)
make seed-demo            # idempotent upsert; then clears Redis catalog:* keys

# Inspect what a reset would touch (no deletes):
make reset-demo-dry

# Delete demo-scoped rows and recreate baseline:
make reset-demo CONFIRM=yes
```

### Verify (stack nginx or direct API)

```bash
make smoke-demo           # login + FOOD-* products via http://localhost:8088
# or:
curl -sS -X POST http://localhost:8088/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer@aptech4.local","password":"CustomerDemo_change_me!"}'
```

### Re-run

- `make seed-demo` repeatedly: upsert-only, stable IDs.
- `make reset-demo CONFIRM=yes` repeatedly: idempotent deletes + same logical dataset after reseed.

## What seed/reset will not do

- Auto-run on every backend startup
- Run under Spring profile `prod`
- Wipe Docker volumes or truncate unrelated tables
- Mix demo rows into Liquibase changelogs
- Treat “database name contains `dev`” as proof of safety
