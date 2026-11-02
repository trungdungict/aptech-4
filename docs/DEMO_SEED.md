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
| Products | FoodMart SKUs (`FOOD-*`) | 48 products; includes zero-stock SKUs; each gets persisted `/uploads/products/{uuid}.ext` from `foodmart-product-images.json` |
| Inventory | per seeded product | `on_hand ≥ reserved`; never negative |
| Journeys | addresses, cart, sample orders, notifications | FoodMart products only |
| Promotion strategies | codes `PROMO-*` | See table below — upsert by code |
| Promotion campaigns | grocery slugs (e.g. `banner-trang-chu`) | Upsert by slug; **no seeded checkout orders** with snapshots |

Seed also **purges retired** legacy `DEMO-*` / `demo-*` / former `DEMO-PROMO-*` / `demo-promo-*` natural keys listed in `DemoSeedMarkers` / `DemoPromotionCatalog` (local-demo guard). It does **not** delete unrelated non-demo rows.

### Promotion fixtures (Phase 8)

Owned by `DemoPromotionCatalog` / `DemoPromotionSeedService`. Re-seed is idempotent and restores the baseline statuses below (so a local “activate for demo” change is reset on the next `make seed-demo`). Visible names are production-like Vietnamese grocery copy (no `Demo`/`Smoke` labels).

| Slug | Default status | Purpose |
|------|----------------|---------|
| `banner-trang-chu` | **ACTIVE** | `HOME_HERO` (`Ưu đãi mua sắm tiết kiệm`) + FoodMart JPG under `/uploads/promotions/bbbbbbbb-cccc-4ddd-8eee-ffffffffffff.jpg` |
| `uu-dai-thanh-vien-10` | PAUSED | 10% order-level (empty eligibility) — activate for cart-wide % |
| `uu-dai-don-hang-15k` | PAUSED | Fixed 15.000₫ (min order 50k) |
| `uu-dai-tao-tuoi-15` | PAUSED | 15% `PRODUCT_DISCOUNT` on `FOOD-FRUIT-APPLE-001` |
| `giam-8-do-uong` | PAUSED | 8% `CATEGORY_DISCOUNT` on `food-beverages` |
| `uu-dai-mix-or-5` | PAUSED | 5% order-level with product **OR** category sets |
| `uu-dai-stack-pct-5` / `uu-dai-stack-fixed-5k` | PAUSED | Stackable pair (5% + 5.000₫, non-compounding) |
| `uu-dai-sap-dien-ra` | SCHEDULED | Future window |
| `uu-dai-tam-dung` | PAUSED | Lifecycle sample |
| `uu-dai-nhap` | DRAFT | Not public |
| `uu-dai-het-han` | EXPIRED | Past window |

Pricing campaigns stay **PAUSED** by default so baseline checkout totals stay predictable; activate in Admin for demos. Hero stays ACTIVE for storefront placement demos. Historical discount snapshots are **not** seeded — use live mock checkout after activating a campaign (or `make smoke-promotions`).

`make seed-demo` copies classpath `db/demo/promo-hero.jpg` and FoodMart product assets (`db/demo/product-images/`) into `backend/.data/uploads/…` and, when container `aptech4-api` is running, syncs those files into the Compose uploads volume so nginx can serve `/uploads/promotions/…` and `/uploads/products/…`. Re-run `make seed-demo` after pulling if seed artwork is missing from the volume.

## Reset scope

Deletes only rows matching:

1. Configured demo emails (`DEMO_ADMIN_EMAIL` / `DEMO_CUSTOMER_EMAIL`) + journey extras
2. Category slugs from `DemoSeedMarkers.resetCategorySlugs()` (FoodMart + retired)
3. Product SKUs from `DemoSeedMarkers.resetProductSkus()` (FoodMart + retired)
4. Promotion strategy codes / campaign slugs from `DemoSeedMarkers.promotionStrategyCodes()` / `promotionCampaignSlugs()`
5. Dependents of those rows (carts, reservations, orders/payments/checkout for those users/products, inventory, `user_roles`, addresses, refresh tokens)

Non-demo users/products/categories/promotions and role reference data are kept. FK constraints stay enabled (no `TRUNCATE` of business tables). Order promotion snapshot amounts remain when campaigns are deleted (`ON DELETE SET NULL`).

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
make smoke-promotions     # promotion journey (creates ephemeral TMP-* rows; checks grocery promo seed)
make smoke-inventory-analytics  # Admin inventory + analytics APIs via nginx (needs DEMO_ADMIN_* / DEMO_CUSTOMER_*)
make e2e-inventory-analytics    # Playwright Admin inventory + analytics UI (same stack + DEMO_*)
# or:
curl -sS -X POST http://localhost:8088/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"customer@aptech4.local","password":"CustomerDemo_change_me!"}'
```

### Re-run

- `make seed-demo` repeatedly: upsert-only, stable IDs (including `PROMO-*` codes / grocery campaign slugs); purges retired `DEMO-PROMO-*` / `demo-promo-*`.
- `make reset-demo CONFIRM=yes` repeatedly: idempotent deletes + same logical dataset after reseed.

## What seed/reset will not do

- Auto-run on every backend startup
- Run under Spring profile `prod`
- Wipe Docker volumes or truncate unrelated tables
- Mix demo rows into Liquibase changelogs
- Treat “database name contains `dev`” as proof of safety
