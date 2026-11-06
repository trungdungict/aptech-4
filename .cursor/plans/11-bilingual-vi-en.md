# P34 — Bilingual Vietnamese / English (VI/EN)

## Goal

Deliver full storefront + Admin support for locales **`vi`** and **`en`**:

1. **UI chrome** localization (React)
2. **Business content** translations (catalog + promotions) with server-side resolution
3. **API locale contract** (`Accept-Language`) with centralized backend policy
4. **Deterministic bilingual demo/seed** suitable for Vitest + Playwright
5. **Admin translation management** without changing pricing, inventory, or promotion *rules*

**This document is the normative plan for P34.** Implementation starts only after P34-T01 review freeze.

**P34-T01 (this task) is audit + planning only.** No API contract changes, no Liquibase execution, no seed rewrite, no `.env` / volume changes, no commit/push.

---

## Prerequisites (already shipped)

| Dependency | Evidence |
|------------|----------|
| Catalog schema | Liquibase `020-catalog`, `080-categories-active`, `130-product-image-url` |
| FoodMart demo seed | `DemoSeedService` + `db/demo/foodmart-catalog.json` (8 cats / 48 SKUs) |
| Promotions | Liquibase `140`–`144`; `DemoPromotionSeedService` / `DemoPromotionCatalog` |
| Demo workflow docs | `docs/DEMO_SEED.md`, ADR-010/011 |
| Public Redis caches | `CatalogCacheKeys` / `PromotionCacheKeys` (no locale today) |
| Frontend API + RQ | `api-client.ts`, feature `*-query-keys.ts` |
| Chat dual-language (isolated) | `frontend/src/features/chat/lib/chat-i18n.ts` |
| Inventory + Analytics | P25–P33 **COMPLETE** |

---

## Audit snapshot (2026-11-02)

### Backend — business content

| Entity | Table | Translatable today | Invariant |
|--------|-------|--------------------|-----------|
| `Product` | `products` | `name`, `description` | `id`, `sku`, `unit_price`, `currency`, `active`, `image_url`, `category_id` |
| `Category` | `categories` | `name`, `description` | `id`, `slug`, `parent_id`, `active` |
| `PromotionStrategy` | `promotion_strategies` | `name` | `code`, `type`, amounts, qty, stackable, priority, active |
| `PromotionCampaign` | `promotion_campaigns` | `name`; **`notes` = admin ops (keep EN / non-localized)** | `slug`, `status`, window, priority, stackable, `strategy_id` |
| `PromotionPlacement` | `promotion_placements` | `title`, `subtitle`, `cta_label` | `placement_key`, CTA targets, images, order, window, enabled |
| `Inventory` | `inventory` | — | quantities |
| Order history copies | `order_items.product_name`, `order_promotion_snapshots.*` | **Historical snapshots** — freeze at write time; do **not** re-localize | IDs, money, status enums |

**No translation tables exist.** Single locale columns only. Latest Liquibase include: `144-order-promotion-discount.yaml`.

### Seed / mock language split (critical)

| Dataset | Source | Current language | Natural keys |
|---------|--------|------------------|--------------|
| Categories + products | `backend/.../db/demo/foodmart-catalog.json` (+ FE mirror `foodmart-demo-data.json`) | **English** display names | slugs `food-*`, SKUs `FOOD-*` |
| Promo strategies/campaigns/placements | `DemoPromotionCatalog` / `DemoPromotionSeedService` | **Vietnamese** visible names; campaign **notes** English ops | codes `PROMO-*`, slugs e.g. `banner-trang-chu` |
| Hero placement | `HERO_TITLE` / `HERO_SUBTITLE` | Vietnamese | image path deterministic UUID |

Seed is **idempotent upsert** (`make seed-demo`); destructive path is **demo-scoped** `make reset-demo CONFIRM=yes` only — never volume wipe for bilingual work.

### Backend locale / errors / cache

| Concern | Today |
|---------|-------|
| `Accept-Language` / `LocaleResolver` / `MessageSource` | **Absent** |
| API errors | Hardcoded English `ApiError` via `GlobalExceptionHandler` / `ApiException` |
| Public catalog cache keys | `catalog:products:list` / `detail` / categories — **no locale** |
| Public promo cache keys | `promotionsHomepage` / placements — **no locale** |

### Frontend

| Concern | Today |
|---------|-------|
| i18n libs | **None** (`package.json`: no i18next / react-intl / lingui) |
| UI chrome | Hardcoded **English** (auth, catalog shell, cart, checkout, admin nav, analytics) |
| Chat | Hand-rolled EN/VI (`chat-i18n.ts`); language in **request body**, not `Accept-Language`; `localStorage` `aptech4.chat.language` default **EN** |
| Money | `Intl.NumberFormat('vi-VN')` + ₫ via `format-price.ts` |
| Dates | Mix: orders `vi-VN`; analytics `en-GB` UTC; notifications browser locale |
| API client | `Accept: application/json` only — **no `Accept-Language`** |
| React Query keys | Locale **not** included |
| Playwright | `e2e/admin-inventory-analytics.spec.ts` asserts **English** labels |

### Plans / state before P34

- `.cursor/plans/` had no bilingual plan (00–10 only).
- `TASKS.md` / `PROGRESS.md` pointed next to P17 after P33; **no P34 registry** until this task.

---

## Approved direction (proposed freeze for review)

These are **recommended defaults** for P34. Mark accepted/changed in T01 review before T02 coding.

| Decision | Recommendation |
|----------|----------------|
| Supported locales | `vi`, `en` only |
| Default locale | `vi` |
| Fallback locale | `vi` |
| API signal | `Accept-Language` (HTTP) |
| Locale ownership | **Central** backend component; controllers must not parse headers |
| Content model | **Entity-specific translation tables** (not generic key/value EAV) |
| Base columns | Keep `products.name` etc. as **canonical VI fallback column** after backfill; translations supply per-locale overrides |
| Historical order text | Keep snapshot language at purchase time |
| Currency display | Continue VND; format via locale (`vi` → `vi-VN`, `en` → **`en-US`** — frozen in T05) |
| Chat language | Migrate onto shared i18n in T05/T07; keep API body `language` until chat backend supports Accept-Language (open) |

---

## Locale policy (normative draft)

### Parsing (implemented in T02 — `AcceptLanguageParser`)

1. Read `Accept-Language` once in `RequestLocaleFilter`; bind `RequestLocaleHolder` + `LocaleContextHolder`.
2. Parse RFC 7231 quality values (`en-US,en;q=0.9,vi;q=0.8`); missing `q` → `1.0`.
3. Normalize regional tags → primary subtag: `en-US` → `en`, `vi-VN` → `vi` (case-insensitive).
4. Order by `q` descending; equal `q` keeps header order; first **supported** tag wins.
5. Empty / missing header → **`vi`**. Unsupported-only → **`vi`**.
6. **`q=0`**: language not acceptable (skipped). **Invalid / out-of-range `q`**: entry ignored.
7. **Wildcard `*`**: never maps to a concrete `AppLocale`; skipped so a lower-`q` supported language can win; wildcard-only → **`vi`**.
8. Typed enum `AppLocale { VI, EN }` with `code()` / `toJavaLocale()`.

### Resolution layers

| Layer | Rule |
|-------|------|
| Request locale | From policy above → request-scoped `AppLocale` |
| Business content | Translation for `AppLocale` if present and non-blank required field; else **`vi`** translation; else **base column** |
| UI chrome (FE) | i18next resource for active locale; missing key → `vi` resource; then key itself only in DEV |
| Validation / API errors | MessageSource (or equivalent) keyed by locale; fallback `vi`; keep machine `error` code English-stable |
| Caching | **Include locale** in public Redis keys for any localized payload |
| Browser persistence | Prefer `localStorage` key e.g. `aptech4.locale` (`vi`\|`en`); sync `document.documentElement.lang`; optional `?lang=` for demos |
| Formatting | Shared FE helpers: `formatMoney(amount, currency, locale)`, `formatDateTime(...)`; stop scattering hard-coded `vi-VN`/`en-GB` |

### Explicit non-goals for locale

- Per-user DB locale preference (optional later)
- More than two locales
- Translating enum *codes* (`PAID`, `LOW`) — translate **labels** in UI only
- Locale-specific prices or SKUs

---

## Translation schema (proposed — do not migrate in T01)

### Why entity-specific tables

- Matches existing entities and Admin CRUD shapes
- FK + `UNIQUE (entity_id, locale)` enforce integrity
- Avoids sparse EAV and untyped keys
- Clear indexes for `(locale)` list joins

### Tables

```text
product_translations
  id UUID PK
  product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE
  locale VARCHAR(5) NOT NULL   -- 'vi' | 'en'
  name VARCHAR(200) NOT NULL
  description TEXT NULL
  UNIQUE (product_id, locale)
  INDEX (locale)

category_translations
  id UUID PK
  category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE
  locale VARCHAR(5) NOT NULL
  name VARCHAR(…) NOT NULL
  description TEXT NULL
  UNIQUE (category_id, locale)
  INDEX (locale)

promotion_strategy_translations
  … strategy_id FK, locale, name NOT NULL, UNIQUE(strategy_id, locale)

promotion_campaign_translations
  … campaign_id FK, locale, name NOT NULL
  -- notes remain on base table (admin ops), not translated

promotion_placement_translations
  … placement_id FK, locale, title, subtitle, cta_label
  UNIQUE(placement_id, locale)
```

Check constraint / app enum: `locale IN ('vi','en')`.

### Base column policy

After backfill:

1. `products.name` / `description` (and category equivalents) hold **Vietnamese canonical** text (default + ultimate fallback).
2. English lives in `*_translations` with `locale='en'`.
3. Optional: also store `locale='vi'` rows mirroring base for symmetry in Admin UI (recommended so Admin always edits translation rows).

### DTO behavior

| Surface | Behavior |
|---------|----------|
| **Public** `ProductResponse` / `CategoryResponse` / `PublicPlacementResponse` | Same JSON shape as today (`name`, `description`, …) but values **resolved** for request locale + fallback. No breaking field renames in v1. |
| **Admin read (list/detail)** | Either resolved locale **or** (preferred for editors) `translations: { vi: {…}, en: {…} }` plus invariants — **contract freeze in T04** |
| **Admin write** | Accept full translation map; require `vi.name` (and strategy/campaign/placement required fields); `en` optional |
| **Analytics / inventory list** | Resolved names for request locale (Admin UI locale) |

Optional response header: `Content-Language: vi|en` (document in T02).

### Migration & backfill strategy (T03 — plan only here)

1. Add translation tables (Liquibase `145+`).
2. **Backfill `en`** from current English FoodMart base columns.
3. **Write `vi`** from a new seed dictionary (T06) into base columns + `vi` translation rows.
4. Promo: treat current Vietnamese names as `vi`; add `en` rows in T06.
5. One deliberate product **without** `en` row to prove fallback.
6. Rollback: drop translation tables in reverse changeset; base columns remain usable.
7. **No** Docker volume delete; existing DBs upgrade via Liquibase on deploy/migrate.

Risk: smoke/E2E that assert English product names will fail until T06+T07 update assertions or use SKU/`data-testid`.

---

## Mock / seed data strategy (mandatory)

### Decision

**Extend in place** (not replace workflow):

| Action | Choice |
|--------|--------|
| FoodMart JSON | **Extend** with locale fields; keep SKU/slug/price/onHand |
| DemoPromotionCatalog | **Extend** with EN strings; keep codes/slugs/rules |
| Seed services | **Update** upsert to write translation rows + canonical VI base |
| FE demo JSON | **Mirror** backend catalog translations for offline/demo helpers |
| Reset markers | Keep natural-key lists; no new destructive scope |
| Execution in T01 | **None** (plan only) |

### Target bilingual fixtures

**Products (48 SKUs)**  
- Every SKU: VI + EN `name`; descriptions VI + EN where present today  
- Same `id`/SKU/price/inventory across locales  
- ≥1 SKU with **complete** VI+EN  
- ≥1 SKU with **missing EN** (fallback demo)  
- ≥1 SKU with **null description** in both or VI-only  

**Categories (8)**  
- VI + EN name/description; stable `food-*` slugs  

**Promotions**  
- Strategy/campaign/placement: VI + EN display strings  
- Codes, slugs, windows, eligibility, stack rules **unchanged**  
- `notes` stay English ops text  

**Determinism**  
- Natural keys unchanged so `make smoke-demo` / `smoke-promotions` / Playwright logins keep working  
- IDs remain stable across idempotent re-seed  

### Exact files to update in T06 (not T01)

| File | Change |
|------|--------|
| `backend/src/main/resources/db/demo/foodmart-catalog.json` | Add `translations` or `nameVi`/`nameEn`/`descriptionVi`/`descriptionEn` |
| `backend/.../seed/FoodMartCatalog.java` (or parser) | Read bilingual fields |
| `backend/.../seed/DemoSeedService.java` | Persist translations + VI base |
| `backend/.../seed/DemoPromotionCatalog.java` | EN constants alongside VI |
| `backend/.../seed/DemoPromotionSeedService.java` | Write promo translations |
| `frontend/src/features/catalog/data/foodmart-demo-data.json` | Mirror |
| `frontend/src/features/promotions/lib/foodmart-fallback.ts` | Bilingual fallback copy |
| `docs/DEMO_SEED.md` | Document locale fields + fallback SKU |
| Possibly `scripts/import-foodmart-demo.mjs` | If it regenerates FE mirror |

### Safe execution procedure (future T06)

1. `make seed-check` (no writes)  
2. Implement code + JSON extensions  
3. `make seed-demo` (idempotent upsert — **no** `reset-deps-volumes`)  
4. Verify counts still 8/48; spot-check one SKU in both locales via API  
5. Only if demo rows are corrupt: `make reset-demo-dry` then `make reset-demo CONFIRM=yes`  
6. **Forbidden:** editing `.env`, `docker compose down -v`, truncating non-demo tables  

### Distinguish demo vs production

Unchanged: seed refuses `prod` profile; Liquibase never embeds FoodMart rows; demo emails/SKUs documented in `DEMO_SEED.md`.

---

## Frontend i18n architecture (T05+)

### Stack

- `i18next` + `react-i18next`
- Typed keys (TS const resources / `ParseKeys`)
- **Namespaces** (no mega-file): e.g. `common`, `auth`, `catalog`, `cart`, `checkout`, `orders`, `admin`, `adminInventory`, `adminAnalytics`, `promotions`, `validation`, `chat`
- Locale module: init, detect, persist, set `document.documentElement.lang`
- `apiRequest` injects `Accept-Language: vi|en`
- React Query: append `locale` to keys for **localized** catalog/promo/admin-name queries
- Language switcher in customer shell + Admin shell
- Migrate `chat-i18n.ts` into `chat` namespace (behavior-compatible)

### Migration order for hardcoded strings

1. `common` + shell (`RootLayout`, errors, empty)  
2. `auth`  
3. `catalog` + product detail  
4. `cart` / `checkout` / `orders`  
5. `promotions` (chrome; content from API)  
6. Admin hub → products/categories → inventory → orders → promotions → analytics  
7. Loading / empty / error / zod messages  

### Tests

- Vitest: i18n provider wrapper; assert keys resolve for `vi`/`en`  
- Prefer `data-testid` for E2E critical controls (add in T07/T08) so Playwright is not EN-only  
- Keep one E2E path that switches language and asserts content fallback  

---

## API locale contract (freeze in T02 / public shape in T04)

| Item | Contract |
|------|----------|
| Request | `Accept-Language: vi` or `en` (regional tags accepted, normalized) |
| Default | `vi` |
| Public JSON | **Compatible** field names; localized values |
| Admin translation CRUD | **Frozen (T04):** separate `GET\|PUT …/translations` with embedded `translations` map (`vi` required, `en` optional). Product/category: `{name,description}`; strategy/campaign: name string; placement: `{title,subtitle,ctaLabel}`. Documented in `backend/docs/OPENAPI.md`. |
| Errors | Localized `message` / `details`; stable `error` token |
| Caching | Locale segment in Redis key |
| Out of scope T01 | Any live OpenAPI/controller change |

---

## Phased roadmap

### Dependency graph

```text
P34-T01 (plan) ──► T02 (locale foundation)
                      │
                      ├──────────► T03 (schema + backfill)
                      │               │
                      │               ▼
                      │            T04 (localized APIs + Admin translation CRUD)
                      │               │
                      ▼               │
                   T05 (FE i18n foundation) ◄── can start after T02 for chrome-only;
                      │                  full content needs T04
                      ▼
                   T06 (bilingual seed) ── needs T03 (+ ideally T04 writers)
                      │
          ┌───────────┴───────────┐
          ▼                       ▼
       T07 (customer web)      T08 (admin web)
          └───────────┬───────────┘
                      ▼
                   T09 (final verification)
```

P17 acceptance should **follow** P34-T09 if bilingual is in graduation scope (update P17 deps when freezing).

---

### P34-T01 — Audit, contract freeze & mock data strategy

| | |
|--|--|
| **Status** | DONE (this document + TASKS evidence) |
| **Scope** | Repo audit; architecture; schema proposal; seed plan; roadmap; state updates |
| **Deps** | P33 complete |
| **AC** | Plan file exists; TASKS P34 registered; no implementation/migrations/seed rewrites |
| **Out** | Coding, Liquibase run, seed mutation, commits |

---

### P34-T02 — Backend locale foundation

| | |
|--|--|
| **Status** | **DONE** (2026-11-02) |
| **Scope** | `AppLocale`, Accept-Language parser, request filter + `RequestLocaleHolder` / `LocaleContextHolder`, CORS `Accept-Language`, tests |
| **Deps** | T01 |
| **Shipped files** | `common/locale/{AppLocale,AcceptLanguageParser,RequestLocaleHolder,RequestLocaleFilter}.java`; `SecurityConfig` filter order + CORS |
| **AC** | Controllers do not parse headers; unsupported → `vi`; unit + filter concurrency tests; security WebMvcTests green |
| **Deviation** | **MessageSource / GlobalExceptionHandler localization deferred** to a later task (changing default messages to `vi` would break current EN-asserting ITs before FE i18n). Locale resolution is ready for T03+. |
| **Verified policy extras** | Invalid/`q∉[0,1]` → skip entry; `q=0` not acceptable; `*` never selects a locale (skipped so lower-q supported can win); wildcard-only → `vi` |
| **Out** | Translation tables, DTO shape changes, FE, seed |

---

### P34-T03 — DB translation schema & safe backfill

| | |
|--|--|
| **Status** | **DONE** (2026-11-02) |
| **Scope** | Liquibase `145-content-translations.yaml`; JPA translation entities; set-based backfill |
| **Deps** | T02 |
| **Shipped** | 5 `*_translations` tables; CHECK `locale IN ('vi','en')`; UNIQUE(parent, locale); FK ON DELETE CASCADE; `idx_*_locale`; rollback = `dropTable` |
| **Backfill decision** | Catalog base columns (current EN FoodMart) → `locale='en'` rows. Promo base columns (current VI demo copy) → `locale='vi'` rows. **Not** in T03: VI catalog dictionary, EN promo copy, missing-EN fixture (T06). Base columns unchanged. |
| **Verified** | Testcontainers schema IT 5/5; Compose migrate smoke applied 7 changesets — product_en=48, category_en=8, strategy_vi=11, campaign_vi=12, placement_vi=1 |
| **AC** | Migrate up verified on empty TC + seeded Compose DB; FKs/uniques/indexes; no volume wipe |
| **Out** | Public API switching to translations; Admin CRUD; full VI copywriting |

---

### P34-T04 — Localized APIs & Admin translation CRUD

| | |
|--|--|
| **Status** | **DONE** (2026-11-02) |
| **Scope** | Resolve public product/category/placement (and related) by `AppLocale`; Admin read/write translation maps; cache key locale; invalidate correctly; MessageSource errors |
| **Deps** | T03 |
| **Verified** | Focused suite 26/26: CatalogLocalization IT (4), PromotionLocalization IT (1), Atomicity IT (1), Catalog/Promotion cache ITs + key unit tests, ContentLocalization + ApiMessageResolver |
| **AC** | Public `vi`/`en` + missing-EN fallback; Admin translation CRUD + RBAC; locale cache keys; stable error codes + localized messages; dual-write rollback |
| **Out** | FE forms wiring (T08); seed dictionary (T06) |

---

### P34-T05 — Frontend i18n foundation

| | |
|--|--|
| **Status** | **DONE** (2026-11-02) |
| **Scope** | i18next + react-i18next; typed `common` namespace; locale persistence; `document.documentElement.lang`; `Accept-Language` in api-client; RQ locale keys for localized queries; LanguageSwitcher; format helpers |
| **Deps** | T02 / T04 locale contract |
| **Verified** | `npm run typecheck` OK; `npm test` **290/290**; `npm run build` OK |
| **AC** | Default `vi`; switcher + persistence; API header; locale query keys; chat language remains independent (`aptech4.chat.language`) |
| **Out** | Full string migration (T07/T08); seed (T06); chat namespace migration deferred to T07 |

---

### P34-T06 — Bilingual mock/seed implementation

| | |
|--|--|
| **Scope** | Extend FoodMart + promo seed per §Mock strategy; write translation rows; document fallback SKU; update `DEMO_SEED.md`; FE mirror JSON |
| **Deps** | T03 (tables); writers from T04 preferred |
| **Status** | **DONE** (2026-11-02) |
| **Shipped** | VI base + VI/EN translation upsert; peas `omitEnglishTranslation` (EN deleted each seed); promo DisplayName EN; FE mirror + locale homepage fallback; DEMO_SEED + seed-check bilingual counts |
| **Verified** | BE IT 2/2; FE focused tests; live seed-check→seed-demo×2→seed-check (`48/47` products, `peas_en=0`) |
| **AC** | `make seed-demo` idempotent; 8/48 preserved; missing-EN SKU stable; no `.env`/volume changes |
| **Out** | UI polish (T07/T08); Compose API rebuild for live Accept-Language if image stale |

---

### P34-T07 — Customer web VI/EN

| | |
|--|--|
| **Scope** | Localize auth, catalog, PDP, cart, checkout, orders, home/promotions chrome; wire content from API locale |
| **Deps** | T04, T05, T06 |
| **Status** | **DONE** (2026-11-02) |
| **Shipped** | Customer namespaces + `t()` wiring; schema factories; locale-aware FoodMart fallback; order status labels; Compose api/web rebuild |
| **Verified** | typecheck; vitest 311/311; build; live Accept-Language EN/VI + peas fallback; Redis locale suffix on catalog keys |
| **AC** | Switch locale updates UI + catalog/promo data; fallback SKU; checkout path intact |
| **Out** | Admin (T08); chat still uses independent `chat-i18n` |

---

### P34-T08 — Admin web VI/EN

| | |
|--|--|
| **Scope** | Admin shell + products/categories/promotions translation editors; orders/inventory/analytics chrome; locale-aware names in tables |
| **Deps** | T04, T05, T06 |
| **Status** | **DONE** (2026-11-02) |
| **Shipped** | `admin` NS; chrome wired; Entity/Name/Placement translation editors; VI-canonical edit forms; E2E locale pin |
| **Verified** | typecheck; admin 126 + full 329 vitest; build; `make e2e-inventory-analytics` 3/3 |
| **AC** | Admin can edit VI+EN; lists respect UI locale; inventory/analytics E2E updated |
| **Out** | Flutter |
| **Runtime closeout** | 2026-11-02 — Compose `web` rebuilt (`--no-deps`); browser EN/VI Admin + translation editors verified on `:8088` |

---

### P34-T09 — Final bilingual verification

| | |
|--|--|
| **Scope** | BE ITs, FE tests, Playwright, seed verification, cache locale keys, docs (OpenAPI notes, DEMO_SEED, ADR if not earlier) |
| **Deps** | T07, T08 |
| **AC** | Checklist green; fallback proven; smoke targets green; P34 marked complete in PROGRESS |
| **Out** | New locales beyond vi/en |

---

## Testing strategy (cross-cutting)

| Layer | Focus |
|-------|-------|
| Unit | Locale parse/q-values; fallback chain; i18n key presence |
| IT | Controllers with `Accept-Language`; cache hit per locale; Admin translation CRUD validation |
| Vitest | Provider + namespace smoke; critical forms |
| Playwright | Language switch + inventory/analytics still operable; one catalog fallback case |
| Seed | `seed-check` / `seed-demo` / smoke scripts after T06 |

---

## Migration risks & rollback

| Risk | Mitigation |
|------|------------|
| Cache serves wrong language | Locale in key + invalidate on translation write |
| EN-only tests break | Update in T06–T08; add `data-testid` |
| Partial backfill | Require `vi` before marking product active (policy in T04) |
| Dual source of truth (base vs translation) | Admin writes update both VI base + `vi` row in one transaction |
| Rollback | Liquibase rollback translation changesets; FE feature-flag switcher optional |

---

## Open decisions (resolve at T01 review)

1. Admin DTO: **Frozen T04** — separate `/translations` endpoints + `translations` map.  
2. EN number/date region: **Frozen T05** — `en-US` (`toIntlLocale('en')`).  
3. Chat API: keep body `language` long-term or align to `Accept-Language`? (**T05:** chat storage/UI stays independent of `aptech4.locale`.)  
4. Should `categories.slug` ever become localized? (**Recommend no** — invariant URL key.)  
5. Analytics historical `productName` from snapshots: show as stored vs resolve live product translation? (**Recommend stored snapshot.**)  
6. Whether P17 depends on P34-T09 before graduation demo.

---

## Explicit out of scope (entire P34)

- Flutter localization  
- Additional locales  
- Locale-specific pricing/tax  
- Translating payment provider messages  
- Rewriting order history rows  
- Generic CMS/EAV translations  
- Production data migration beyond Liquibase + demo seed  
- Committing/pushing as part of planning  

---

## T01 deliverables checklist

- [x] Repository audit (BE/FE/seed/docs/plans/state)  
- [x] Locale policy draft  
- [x] Translation schema proposal  
- [x] Mock/seed strategy + file list + safe procedure  
- [x] FE i18n architecture  
- [x] Phased roadmap T01–T09 with deps/AC/risks  
- [x] Plan file `.cursor/plans/11-bilingual-vi-en.md`  
- [x] TASKS / PROGRESS / CURRENT_SESSION updates  
- [ ] Reviewer freeze of open decisions (human)  

**Stop here. Do not start P34-T02 until this plan is reviewed.**
