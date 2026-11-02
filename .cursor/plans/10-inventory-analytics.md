# P25–P31 — Admin Inventory Management & Analytics Dashboard

## Goal

Deliver two Admin product modules on real persisted data:

1. **Inventory Management** — list/search stock, low/OOS visibility, safe adjustments, clear separation from catalog product CRUD.
2. **Analytics Dashboard** — order/revenue/inventory/promotion KPIs from orders, order lines, promotion snapshots, and live inventory.

**This document is the normative plan.** Open business decisions were **approved 2026-11-01** (see §Approved decisions). Implementation proceeds under those freezes.

**Normative reservation semantics remain** [ADR-006](../decisions/ADR-006-inventory-reservation.md). No ledger ADR — deferred.

## Prerequisites (already shipped)

| Dependency | Evidence |
|------------|----------|
| `inventory` + soft-hold reservation (ADR-006) | Liquibase `020-catalog`, `100-inventory-reservation` |
| Admin absolute set on-hand | `GET/PUT /api/v1/admin/products/{id}/inventory` |
| Product edit inventory form | `ProductInventoryForm.tsx` (edit only) |
| Checkout reserve → confirm/release | `CheckoutService` + `InventoryReservationService` |
| Chat-only analytics formulas | **Shared** `com.aptech.aptech4.analytics.AdminAnalyticsService` + `AdminChatTools` (P28) |
| Admin shell + RBAC | `RequireAdmin`, `/api/v1/admin/**` |
| Shared FE pagination | `components/pagination` (default size 10) |
| Order discount snapshots | `orders.discount_amount`, `order_promotion_snapshots` |

## Audit snapshot (2026-11-01)

### Inventory — capability matrix

| Capability | Status | Notes |
|------------|--------|-------|
| Per-product `quantity_on_hand` / `quantity_reserved` / available | **Implemented** | `available = on_hand − reserved` |
| Admin GET/PUT absolute on-hand | **Implemented** | Nested under product; `onHand >= reserved` |
| Product CRUD never writes stock | **Implemented** | Documented + tested |
| Checkout soft-hold reserve/confirm/release | **Implemented** | Cart does **not** reserve (by design) |
| Concurrency (conditional SQL + pessimistic locks) | **Implemented** | No `@Version` |
| Admin inventory **list** / search / filter / paginate REST | **Implemented (P26-T01)** | `GET /api/v1/admin/inventory` |
| Low-stock / OOS Admin REST | **Implemented (P26-T01)** | List `stockStatus` + `/summary`; chat tools unchanged |
| Relative adjust (+/−) with reason | **Deferred** | Absolute set only (approved) |
| Stock movement / audit ledger table | **Deferred** | Approved: defer in P26 |
| Dedicated Inventory Admin UI route | **Implemented (P27)** | `/admin/inventory` + adjust sheet; product edit form retained |
| Optimistic locking column | **Missing** | Current model sufficient unless ledger approved |

### Analytics — capability matrix

| Capability | Status | Notes |
|------------|--------|-------|
| Order counts by status (UTC window) | **Yes** | Shared service (P28) + Admin REST (P29-T02) |
| Revenue / AOV / sales summary | **Yes** | Shared revenue/orderCount; AOV on overview REST only (P29-T02) |
| Top-selling products | **Yes** | Chat + `GET .../products/top` (P29-T02) |
| Low/OOS / inventory risk | **Partial** | Chat tools + P26 inventory summary embed; dedicated risk REST not in P29 |
| Promotion discount totals | **Yes** | `GET .../promotions/summary` (snapshots + `orders.discount_amount`) |
| Time-series (orders/revenue by day) | **Yes** | `GET .../orders/timeseries` UTC day zero-fill (both series) |
| Admin `/admin/analytics` UI | **Yes** | P30-T02 `/admin/analytics` (CSS/SVG/tables) |
| Chart library in FE | **None installed** | Use CSS/SVG/tables (approved); no new dep without approval |

### Frontend Admin surface today (audited 2026-11-01)

| Route | Role |
|-------|------|
| `/admin` | Module hub only — **no fabricated KPIs** (`AdminOverviewPage`) |
| `/admin/products` (+ inventory on **edit**) | Catalog + absolute stock |
| `/admin/inventory` | List + summary + adjust sheet (P27) |
| `/admin/categories`, `/admin/orders`, `/admin/promotions/*` | Existing |
| `/admin/analytics` | **Exists** (P30) — date range + KPIs + tables/SVG |

**FE audit facts:** `package.json` has **no** recharts/chart.js/d3/nivo. Stack: React 19, RR7, TanStack Query 5, Zod, Radix/shadcn-style `Card`/`Badge`/`Skeleton`/`Button`/`Input`/`Label`/`Sheet`, `admin-ui` table tokens, inventory-style per-section loading/error/retry. Money display via `ProductPrice`. Auth: `RequireAdmin` + `apiRequest({ auth: true })`.

---

## Proposed Inventory scope

### In scope (recommended)

1. Admin inventory **list** API: join product + inventory; `q` (name/SKU), `stockStatus` (`ALL|IN_STOCK|LOW|OUT`), `active`, sort, page/size.
2. Reuse existing `GET/PUT .../products/{id}/inventory` for detail/set (or thin alias under `/admin/inventory/{productId}`).
3. Admin UI `/admin/inventory` — table with available/on-hand/reserved, filters, pagination, link to product, inline/adjust drawer using existing PUT.
4. Summary strip: total SKUs with rows, low count, OOS count (threshold configurable).
5. Authorization: `ROLE_ADMIN` only; preserve reservation invariants.

### Out of scope unless approved

- Stock movement ledger / adjustment reasons table (new Liquibase).
- Relative `PATCH` delta API (can be UX-only computing new absolute from current).
- Changing cart to reserve stock.
- Customer-facing inventory quantities beyond `inStock`.

### Classification detail

| Capability | Class |
|------------|-------|
| View stock by product/SKU | Partial → complete via list API + UI |
| Search/filter/sort/paginate | Missing (list API) |
| Low/OOS visibility | Partial (repo queries + chat) → Admin REST + UI |
| Stock adjustment | Partial (absolute PUT) |
| Adjustment reason/audit trail | Missing — **decision** |
| Movement history | Missing — **decision** |
| Concurrency safety | Implemented — reuse |
| Separation from catalog edit | Implemented — keep; inventory page supplements |
| Auth/validation | Implemented patterns — extend |
| Checkout reservation integration | Implemented — do not break |

---

## Proposed Analytics scope

### Metric definitions (real data only)

Timezone for all windows: **UTC** (match `AdminAnalyticsService`). Default window: last **30 days** unless UI overrides (`from`/`to` Instant).

| Metric | Formula | Source | Schema OK? | Notes |
|--------|---------|--------|------------|-------|
| Orders in range | `COUNT(*)` where `placed_at ∈ [from,to)` | `orders` | Yes | All statuses for “total placed”; break out by status |
| Order status distribution | `COUNT` group by `status` in range | `orders` | Yes | Include `PENDING_PAYMENT`, `FAILED`, `CANCELLED` |
| Revenue | `SUM(total_amount)` where status ∈ `REVENUE_STATUSES` | `orders` | Yes | **PAID, PROCESSING, SHIPPED, COMPLETED** — excludes unpaid/failed/cancelled |
| Revenue order count | Same filter as revenue | `orders` | Yes | |
| AOV | `revenue / revenue_order_count` (0 if count=0) | derived | Yes | Never invent |
| Orders over time | Daily buckets UTC of placed orders (all or revenue-only — **decision**) | `orders.placed_at` | Yes | Needs date_trunc aggregation |
| Revenue over time | Daily `SUM(total_amount)` for revenue statuses | `orders` | Yes | |
| Best sellers | `SUM(line qty)`, `SUM(line snapshots)` group by product | `order_items` + parent order status filter | Yes | Use line snapshots, not live catalog price |
| Sales by category | Join line → product.category (or snapshot category if present) | lines + products | **Partial** | Prefer product.category_id at query time; historical rename drift possible |
| Promotion discount total | `SUM(orders.discount_amount)` for revenue statuses in range | `orders` | Yes | |
| Promotion usage count | Orders with `discount_amount > 0` or snapshot rows | orders / `order_promotion_snapshots` | Yes | Prefer snapshot existence for “applied” |
| Promo breakdown | `SUM(snapshot.discount_amount)` group by strategy/campaign codes | `order_promotion_snapshots` | Yes | Historical names in snapshot |
| Inventory SKU count | Count **all catalog products** (active + inactive) | `products` LEFT inventory | Yes | P26: missing inventory row still counted |
| Low-stock count | `0 < available <= threshold` | products + inventory | Yes | Threshold: **`aptech.inventory.low-stock-threshold`** default **5** |
| OOS count | `available <= 0` (missing row → 0) | products + inventory | Yes | Same def as list `stockStatus=OUT` |

**Canceled / failed / unpaid:** excluded from **revenue** and **AOV**; included in status distribution and optional “placed orders” total.

**Do not** seed fake analytics values. Demo seed already produces orders/inventory for local charts.

### Chart strategy

- **Approved:** tables + CSS/SVG only — **no** chart library.

---

## Architecture / API plan

### Inventory REST — **P26-T01 DONE (2026-11-01)**

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/admin/inventory` | Page of product+stock; filters `q`, `stockStatus`, `active`, `page`, `size`, `sort` |
| `GET` | `/api/v1/admin/inventory/summary` | `{ totalProducts, inStockCount, lowStockCount, outOfStockCount, lowStockThreshold, availableDefinition }` |
| Existing | `GET/PUT /api/v1/admin/products/{id}/inventory` | Absolute write path (unchanged) |

DTO: `productId`, `sku`, `name`, `active`, `categoryId`, `categoryName`, `hasInventoryRow`, `quantityOnHand`, `quantityReserved`, `available`, `updatedAt`.

Auth: Admin only. Pagination: Spring `Pageable` → existing `PageResponse` (0-based). Sort whitelist + `id` tie-breaker.

### Shared analytics package — **P28-T01 DONE (2026-11-01)**

| Type | Location |
|------|----------|
| Metric constants / AOV helper | `analytics.AnalyticsMetrics` |
| Date window normalize | `analytics.AnalyticsDateRange` (`placed_at >= from AND < to`, UTC label, default 30d, max 366d) |
| Shared service | `analytics.AdminAnalyticsService` (`@PreAuthorize ADMIN`) |
| Chat callers | `AdminChatTools`, `AdminDeterministicAnalyticsService`, `AdminInventoryFacade` |

**Preserved semantics:** `REVENUE_STATUSES` = PAID/PROCESSING/SHIPPED/COMPLETED; attention = PAID+PROCESSING all dates; sales/top/cancel use default 30d; top sellers use order_item snapshots; empty → zeros / `empty=true`, never invent. Chat inventory low-stock still uses `aptech.ai.low-stock-threshold` (not inventory UI key).

**Ambiguities frozen in P29-T01 contract below** (AOV on REST only; timeseries; promotions; category join caveat).

### Analytics REST contract — **P29-T01 FROZEN (2026-11-01)**

Audit source: `AnalyticsMetrics`, `AnalyticsDateRange`, `AdminAnalyticsService`, `ShopOrderRepository`, `OrderItem` (no category snapshot), `OrderPromotionSnapshot` (ADR-020 denormalized), `AdminInventorySummaryResponse` (P26), `SecurityConfig` (`/api/v1/admin/**` → `ROLE_ADMIN`), `ApiError`.

**P29-T01:** contract freeze (docs). **P29-T02 DONE (2026-11-01):** controllers/DTOs/service/repo + ITs shipped.

#### Shared query conventions (all dated endpoints)

| Item | Contract |
|------|----------|
| Auth | `GET /api/v1/admin/analytics/**` — Admin JWT (`ROLE_ADMIN`); 401 anonymous; 403 non-admin |
| Params | `from`, `to` optional ISO-8601 `Instant` (UTC). Omit either → `AnalyticsDateRange.normalize` |
| Window | `placed_at >= from AND placed_at < to` |
| Defaults | last **30** days ending at `to` or `now`; max span **366** days |
| Validation | `from < to` required; span > 366 → **400** `ApiError` (`from must be before to` / range message) |
| Timezone field | response always includes `timezone: "UTC"` |
| Empty | numeric zeros; lists `[]`; `empty: true` when the primary count is 0; **never fabricate** |
| Errors | existing `ApiError` JSON; no new error envelope |
| Chat | do **not** change chat `salesSummary` map (no AOV there) |

#### Endpoint list (small coherent set)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/admin/analytics/overview` | KPI overview for range + live attention + inventory snapshot |
| `GET` | `/api/v1/admin/analytics/orders/status` | Order counts by status in range |
| `GET` | `/api/v1/admin/analytics/orders/timeseries` | Daily **placed-order count** + daily **revenue** (both series) |
| `GET` | `/api/v1/admin/analytics/products/top` | Best sellers (order_item snapshots) |
| `GET` | `/api/v1/admin/analytics/categories/sales` | Sales by **current** product `category_id` |
| `GET` | `/api/v1/admin/analytics/promotions/summary` | Discount totals + snapshot breakdown |

**Inventory:** do **not** add a duplicate analytics inventory path. Dashboard uses existing **`GET /api/v1/admin/inventory/summary`** (P26, `aptech.inventory.low-stock-threshold`). Overview embeds the same figures for convenience.

Optional query on list-like analytics: `limit` (default **10**, max **20**) for `products/top` and `categories/sales` only.

#### 1. Overview — `AdminAnalyticsOverviewResponse`

`GET /api/v1/admin/analytics/overview?from&to`

| Field | Type | Formula / source |
|-------|------|------------------|
| `from`, `to` | Instant | normalized window |
| `timezone` | string | `"UTC"` |
| `currency` | string | `"VND"` |
| `revenueStatuses` | string[] | PAID, PROCESSING, SHIPPED, COMPLETED |
| `placedOrderCount` | long | `COUNT(*)` all statuses in window |
| `revenueOrderCount` | long | count where status ∈ REVENUE_STATUSES |
| `revenueTotal` | decimal | `SUM(total_amount)` REVENUE_STATUSES |
| `averageOrderValue` | decimal | `AnalyticsMetrics.averageOrderValue(revenueTotal, revenueOrderCount)` — **REST only** |
| `cancellationCount` | long | status = CANCELLED in window |
| `cancellationRate` | double | `cancellationCount / placedOrderCount` (0 if placed=0) |
| `statusCounts` | map/object | all `OrderStatus` → long (zeros filled) |
| `attention` | object | `{ paid, processing, totalMatching }` — **all dates**, ATTENTION_STATUSES (same as chat `attentionCounts`) |
| `inventory` | object | same fields as `AdminInventorySummaryResponse` (P26 threshold) |
| `empty` | boolean | `true` iff `placedOrderCount == 0` |
| `revenueDefinition` | string | `AnalyticsMetrics.REVENUE_DEFINITION` |

#### 2. Status distribution — `AdminAnalyticsStatusResponse`

`GET /api/v1/admin/analytics/orders/status?from&to`

Mirrors chat `orderCountsByStatus` shape as typed DTO: `from`, `to`, `timezone`, `counts` (every status, zeros filled), `total`, `empty` (`total == 0`).

#### 3. Timeseries — `AdminAnalyticsTimeseriesResponse`

`GET /api/v1/admin/analytics/orders/timeseries?from&to`

Approved: **both** series.

| Field | Type | Notes |
|-------|------|-------|
| `from`, `to`, `timezone` | | normalized |
| `bucket` | string | `"DAY"` (only granularity in P29) |
| `points` | array | contiguous UTC calendar days |
| `points[].date` | string | `yyyy-MM-dd` UTC |
| `points[].placedOrderCount` | long | all statuses with `placed_at` in that UTC day ∩ window |
| `points[].revenueTotal` | decimal | SUM `total_amount` for REVENUE_STATUSES that day |
| `empty` | boolean | no orders in whole window |

**Zero-fill:** emit one point for every UTC day `d` where day-start ∈ `[from, to)` (i.e. from truncated day through the last day that intersects the half-open window). Days with no rows → counts/revenue **0**. Month/year boundaries: UTC date arithmetic only (no local TZ). Spans that cross months/years are a continuous UTC day sequence.

#### 4. Top products — `AdminAnalyticsTopProductsResponse`

`GET /api/v1/admin/analytics/products/top?from&to&limit`

Same semantics as chat `topSellingProducts`: group `order_items` by sku/name/productId; filter parent order REVENUE_STATUSES + window; `unitsSold = SUM(quantity)`, `revenue = SUM(line_total)`; order by unitsSold desc; note that snapshots ≠ live catalog price. `empty` when items empty.

#### 5. Category sales — `AdminAnalyticsCategorySalesResponse`

`GET /api/v1/admin/analytics/categories/sales?from&to&limit`

| Rule | Contract |
|------|----------|
| Attribution | Join `order_items.product_id` → `products.category_id` → `categories` (**live** category) |
| **Caveat** | **Historical category attribution is not preserved.** Renames/moves after the order change past reporting. `order_items` has **no** category snapshot column. |
| Status / date | Parent order ∈ REVENUE_STATUSES; `orders.placed_at` window |
| Metrics | `categoryId`, `categoryName`, `unitsSold` (SUM qty), `revenue` (SUM line_total), optional `orderLineCount` |
| Missing product | Lines with `product_id IS NULL` (deleted product) **excluded** from category breakdown (still visible in top sellers via sku snapshot) |
| Sort | revenue desc, then unitsSold desc; `limit` applies |
| `empty` | no qualifying lines |

#### 6. Promotions — `AdminAnalyticsPromotionSummaryResponse`

`GET /api/v1/admin/analytics/promotions/summary?from&to`

| Metric | Formula |
|--------|---------|
| Window / statuses | Parent `orders.placed_at` in range; order status ∈ **REVENUE_STATUSES** |
| `discountTotal` | `SUM(orders.discount_amount)` for those orders |
| `ordersWithPromotionCount` | COUNT of those orders with **≥1** `order_promotion_snapshots` row (prefer snapshots over `discount_amount > 0`) |
| `revenueOrderCount` | revenue orders in window (same as overview) |
| `breakdown[]` | Group snapshots joined to qualifying orders by `campaignSlug` + `strategyCode` (nullable code → `""`); SUM `snapshot.discount_amount`; COUNT distinct order ids; expose snapshot `campaignName`, `strategyName`, `strategyType` |
| Deleted campaigns | Use **snapshot** names/slugs/codes (ADR-020). `campaignId`/`strategyId` may be null or stale — display names from snapshot; do not require live campaign rows |
| `empty` | `ordersWithPromotionCount == 0` |

#### Authorization & OpenAPI

- Covered by `/api/v1/admin/**` security matcher.
- OpenAPI tag `Admin Analytics`; document formulas on operations.
- Service layer should keep `@PreAuthorize("hasRole('ADMIN')")` on shared analytics methods (already on `AdminAnalyticsService`).

#### P29-T02 test matrix

| Case | Assert |
|------|--------|
| Authz | 401 / 403 on all analytics GETs |
| Default window | omit from/to → ~30d; response `from`/`to` present |
| Range validation | inverted / >366d → 400 |
| Empty DB / empty window | zeros, `empty=true`, no invented SKUs |
| Status zeros | every `OrderStatus` key present |
| Revenue exclusion | PENDING_PAYMENT / CANCELLED / FAILED not in revenueTotal |
| Timeseries | both series; zero-filled days; UTC `yyyy-MM-dd`; cross-month span continuous |
| Top products | matches order_item snapshot math; limit respected |
| Category caveat | documented; live `category_id`; null product_id excluded |
| Promotions | snapshot breakdown survives campaign delete; revenue-status filter |
| Inventory embed | overview.inventory uses **inventory** threshold (P26), not AI threshold |
| Chat regression | existing AdminAnalytics* / chat tests still green; chat salesSummary still without AOV |

#### Out of scope for P29

- Fact tables / migrations / mock series
- Changing chat tool JSON shapes
- Hourly/weekly buckets
- Relative inventory adjust / stock ledger
- Frontend dashboard (P30)

### Frontend (inventory — shipped)

| Route | Page |
|-------|------|
| `/admin/inventory` | List + filters + pagination + adjust via existing inventory API |

### Analytics dashboard UI contract — **P30-T01 FROZEN (2026-11-01)**

Audit source (verified in tree): `nav.ts`, `AdminOverviewPage`, `AdminLayout`/`AdminNav`, `admin-lazy-routes`/`router.tsx`, `AdminInventoryPage` + query keys/API, `admin-ui.ts`, `package.json`, `RequireAdmin`, `api-client`, P29 DTOs under `com.aptech.aptech4.analytics.api`.

**No dashboard implementation in P30-T01.** Implementation = **P30-T02**.

#### Route, nav, and overview hub

| Item | Contract |
|------|----------|
| Route | `/admin/analytics` under existing Admin layout (`RequireAdmin` → `LazyAdminLayout`) |
| Lazy entry | `LazyAdminAnalyticsPage` in `admin-lazy-routes.tsx` + `Route path="analytics"` in `router.tsx` |
| Nav | Add `{ id: 'analytics', label: 'Analytics', to: '/admin/analytics', description: '…' }` to `ADMIN_NAV_ITEMS` (after Inventory) |
| Icon | Lucide chart/analytics icon in `AdminOverviewPage` `MODULE_ICONS` |
| Breadcrumb | Works via `pathname.startsWith(item.to)` once nav `to` is set |
| Hub copy | Keep overview as **module links only** — do **not** invent KPI numbers on `/admin`; optional one-line "Open Analytics for live KPIs" is OK |
| Deep links | From inventory summary → optional link to Analytics (nice-to-have in T02; not required) |

Feature folder (mirror inventory):

```
features/admin/analytics/
  api/admin-analytics-api.ts
  hooks/admin-analytics-query-keys.ts
  types.ts
  lib/analytics-date-range.ts   # URL ↔ Instant helpers only (no formula reimplementation)
  pages/AdminAnalyticsPage.tsx
  pages/AdminAnalyticsPage.test.tsx
  components/…                  # section presentational pieces as needed
```

#### Page layout and section order (single scroll page)

1. **Header** — title, lead (UTC + revenue-status tip), Refresh
2. **Date range bar** — card like inventory filters: presets + from/to + Apply
3. **Overview KPIs** — revenue, AOV, cancellation rate, order counts, attention, inventory embed
4. **Orders timeseries** — dual series (revenue + placed count)
5. **Order status breakdown** — all statuses zero-filled
6. **Top products** — table (+ limit)
7. **Category sales** — table (+ limit + attribution caveat)
8. **Promotion summary** — totals + breakdown table

Responsive: stack sections; KPI grid `1 → 2 → 3/4` cols; tables use `adminTableWrapClass`; charts full width.

#### Endpoint → section mapping

| Section | Endpoint | Primary fields |
|---------|----------|----------------|
| Overview KPIs | `GET /api/v1/admin/analytics/overview` | `revenueTotal`, `averageOrderValue`, `cancellationRate`/`cancellationCount`, `placedOrderCount`, `revenueOrderCount`, `attention.*`, `inventory.*`, `empty`, `from`/`to`/`timezone`/`currency`/`revenueDefinition` |
| Timeseries | `GET .../orders/timeseries` | `points[].{date,placedOrderCount,revenueTotal}`, `bucket`, `empty` |
| Status | `GET .../orders/status` | `counts`, `total`, `empty` — **independent** of overview `statusCounts` for per-section retry |
| Top products | `GET .../products/top?limit` | `items[]`, `note`, `empty` |
| Category sales | `GET .../categories/sales?limit` | `items[]`, `attributionNote`, `empty` |
| Promotions | `GET .../promotions/summary` | `discountTotal`, `ordersWithPromotionCount`, `revenueOrderCount`, `breakdown[]`, `empty` |

Do **not** call P26 `/admin/inventory/summary` separately — use `overview.inventory`.
Do **not** add backend endpoints or redesign DTOs.

#### Date range state and URL behavior

| Rule | Contract |
|------|----------|
| Semantics | Backend owns window (`placed_at >= from AND < to`, UTC, default 30d, max 366d) |
| FE must not | Reimplement `AnalyticsDateRange.normalize`; only build Instant query params |
| Initial visit (no query) | Omit `from`/`to` on all six requests → backend default 30d |
| Sync after first success | `replace` URL with overview response `from`/`to` Instant strings so refresh/share preserves window |
| Explicit range | Apply writes `?from=<Instant>&to=<Instant>`; all section queries use those params |
| Date inputs | Native `<input type="date">` (no date lib). UTC calendar days: `from` = start of picked day; exclusive `to` = start of day **after** inclusive end date |
| Labeling | Show "UTC"; note exclusive `to`; display normalized window from last successful overview |
| Presets | Last 7 / 30 / 90: Instant pair from browser clock, then Apply (span ≤ 366) |
| Limit | Top/category only; default **10**, max **20** (optional URL `limit`) |
| Client validation | Both dates required on Apply; reject inverted / >366d inline before API |
| Stale races | Query keys include range; pass AbortSignal; **no** previous-range `placeholderData` |

#### Query keys and refetch

```ts
adminAnalyticsQueryKeys = {
  all: ['admin', 'analytics'],
  overview: (range) => [...all, 'overview', range],
  status: (range) => [...all, 'status', range],
  timeseries: (range) => [...all, 'timeseries', range],
  topProducts: (range, limit) => [...all, 'topProducts', range, limit],
  categorySales: (range, limit) => [...all, 'categorySales', range, limit],
  promotions: (range) => [...all, 'promotions', range],
}
// range = { from?: string; to?: string }
```

Six independent `useQuery` hooks; `auth: true`; `retry: false` (match inventory). Page Refresh → refetch all active queries. Date/limit changes change keys → automatic fetch.

#### Charting decision (frozen)

| Decision | Detail |
|----------|--------|
| Library | **None installed** (`package.json` has no chart package) |
| Choice | **CSS/SVG + tables** only (approved decision #6) |
| Timeseries | Dual SVG/CSS series over zero-filled `points[]` with legend |
| Status | Horizontal CSS bars for every status (zeros listed) |
| Tables | Top products, categories, promotion breakdown |
| New dependency | **Not approved** for P30 without a separate decision |

#### Section UX notes (API-faithful)

- **Overview:** Attention = all dates (not window-bound). Inventory strip links to `/admin/inventory`. `empty=true` → zeros + callout; never fabricate. Show `revenueDefinition`. Money via `ProductPrice`.
- **Top products:** If `productId`/`path` null → badge "Catalog unlinked"; **do not merge** null vs non-null productId groups.
- **Categories:** Always show `attributionNote` (live category; historical not preserved).
- **Promotions:** Trust snapshot `campaignName`/`slug`; empty `strategyCode` → display `—`; no live campaign fetch.

#### Loading / empty / error / auth

| State | Contract |
|-------|----------|
| Initial | Per-section skeletons (`role="status"`) like inventory |
| Partial failure | Section alert + Retry for that query only; others keep data |
| Full blank | Only auth gate (`RequireAdmin` → login / forbidden) |
| Empty | Real zeros + dashed empty card; **no mock series** |
| 400 | Surface `ApiClientError.userMessage` on date bar / section |
| 401/403 | Existing api-client session + forbidden UX |

#### Accessibility & responsive

Section `h2`s; charts need text alternative (summary or companion table); focus-visible controls; stack on mobile; color not sole encoding.

#### Out of scope for P30

New backend fields/endpoints; chat UI; chart npm dep; fabricated metrics; inventory redesign.

#### P30-T02 implementation checklist

1. Types + Zod parsers matching P29 JSON
2. `admin-analytics-api.ts` six GETs
3. Query keys + page with URL range sync
4. Nav + lazy route + overview icon
5. Six independent section queries/states
6. CSS/SVG timeseries + status; tables for rest
7. Vitest matrix below
8. `npm run typecheck` / `test` / `build`

#### P30-T02 test matrix

| Case | Assert |
|------|--------|
| Route/nav | Admin can open `/admin/analytics`; customer forbidden; nav link present |
| Default range | Omit params first; then URL syncs to overview `from`/`to` |
| Apply range | Explicit Instants on all six calls; exclusive `to` labeled |
| Validation | Inverted / >366d client-blocked; API 400 handled |
| Overview | Revenue/AOV/cancel/attention/inventory from overview only |
| Timeseries | Zero days + both series; empty message when `empty` |
| Status | All statuses including zeros |
| Top products | Limit; unlinked badge when `productId` null |
| Categories | Attribution note; limit |
| Promotions | Snapshot names; empty breakdown |
| Partial error | One section Retry does not clear others |
| No mocks | Empty APIs → zeros/empty UI, not fake SKUs |

### Tests

- Backend IT: list filters, summary counts, adjust still respects reserved, analytics formulas match chat service, authz 403.
- FE Vitest: pages load, filters, empty states, no fake numbers when API empty.
- Optional smoke Make target after UI lands.
- Do **not** treat chat SSE as the dashboard contract.

### Seed / local demo

- Reuse `make seed-demo` FoodMart products + inventory + journey orders.
- Optional: document which seed SKUs are OOS/low for demos.
- Never insert synthetic “analytics_facts” tables.

---

## Phase map

| Phase | Name | Depends on |
|-------|------|------------|
| **P25** | Inventory & Analytics audit + requirements freeze | — |
| **P26** | Inventory Admin list/summary APIs + invariant verification — **DONE** | P25 decisions |
| **P27** | Inventory Admin UI — **DONE** (`/admin/inventory` + adjust sheet) | P26 |
| **P28** | Analytics metric freeze + shared service extraction — **DONE** | P25 decisions |
| **P29** | Analytics Admin REST — **DONE** (T01 contract + T02 implement) | P28 |
| **P30** | Analytics dashboard UI — **DONE** (T01 contract + T02 implement) | P29 |
| **P31** | Smoke, docs, nav/overview links, final verification — **DONE** | P27 + P30-T02 |

P28–P29 may proceed in parallel with P26–P27 after P25 decisions.

**Roadmap status (2026-11-01):** Inventory + Analytics **P25–P31 COMPLETE**.

**Verification hardening (P32-T01, 2026-11-01):** Playwright Admin inventory/analytics E2E + CatalogCache/FE flake hardening — **DONE** (does not reopen feature scope).

---

## Approved decisions (2026-11-01)

1. **Stock audit trail:** **Defer** ledger/audit-history table — not in P26.
2. **Adjust UX:** **Absolute-only** via existing inventory PUT — no relative delta API.
3. **Low-stock threshold:** dedicated **`aptech.inventory.low-stock-threshold`**, default **5** (not AI key).
4. **Timeseries series:** include **both** order count and revenue series (P29).
5. **Category sales:** use current **`category_id`**; document that historical category attribution is not preserved.
6. **Charts:** existing **CSS/SVG/table** only — no chart dependency.
7. **Inventory list universe:** **all catalog products** (active + inactive) with status filtering.

---

## Risks

| Risk | Mitigation |
|------|------------|
| Duplicating analytics formulas in chat vs REST | Extract shared package/service; chat tools call same code |
| Breaking reservation invariants on adjust | Keep `onHand >= reserved`; IT with concurrent checkout |
| Heavy timeseries queries | Index-friendly `placed_at` ranges; cap range length |
| FE chart bloat | Prefer tables/CSS first |
| Scope creep into catalog redesign | Inventory page separate; product form keeps absolute set |

## P26-T02 — Invariant verification (2026-11-01)

**Result:** No production defect. Absolute PUT + ADR-006 reservation semantics preserved. Two gap ITs added; no model/API changes.

### Invariants → coverage

| Invariant | Primary coverage |
|-----------|------------------|
| Admin absolute `quantityOnHand` set | `AdminProductInventoryIntegrationTest.adminCanViewAndUpdateInventory_*` |
| `quantityReserved` not writable via Admin PUT | **New** `putIgnoresClientSuppliedQuantityReserved` (DTO has only `quantityOnHand`) |
| Reject `onHand < reserved` | `rejectsNegativeInvalidAndBelowReservedQuantities`; also list IT PUT path |
| Reject negative / null / missing on-hand | same |
| Concurrent Admin PUTs keep `onHand >= reserved` | `concurrentAdminUpdatesCannotProduceNegativeOrBreakReservedInvariant` |
| Admin + active reservation consistency | `checkoutReservationReflectsAdminStockUpdates`; **new** `concurrentAdminUpdateAndReservePreserveInvariants` |
| Reserve cannot oversell / make available negative | `InventoryReservationIntegrationTest` (conflict + `concurrentReservesDoNotOversell`); conditional `tryReserve` SQL |
| Release/confirm idempotent; no double stock apply | `reserveReleaseConfirmIdempotencyExpiryAndOwnership` |
| Failed checkout releases hold; cart kept | `CheckoutIntegrationTest.checkoutSuccessFailureIdempotencyAndOwnership` (`assertInventory` after FAIL) |
| Concurrent checkout no oversell | `CheckoutIntegrationTest.concurrentCheckoutDoesNotOversell` |
| Admin RBAC on inventory PUT | `adminCanViewAndUpdateInventory_customerAndAnonymousForbidden` |
| Pessimistic lock on Admin set | `InventoryRepository.findByProductIdForUpdate` used by `AdminInventoryService.setOnHand` |

### Known non-gaps / out of scope

- Relative delta API / stock ledger: **deferred** (approved decisions).
- Unrelated flake observed once: `PromotionCacheIntegrationTest.placementsCacheInvalidatedOnActivate` (null assert); green on re-run — not inventory.

## Confirmation

- **P25-T01:** planning/audit only — docs.
- **P26-T01:** list/summary REST + ITs shipped; no ledger migration; no `.env` edit; reservation semantics unchanged.
- **P26-T02:** invariants verified; gap ITs only; **P26 complete**.
- **P27-T01/T02:** `/admin/inventory` list + summary + absolute adjust sheet; FE tests/build green; **P27 complete**.
- **P28-T01:** shared `analytics` package + metric freeze; chat compatibility preserved; **P28 complete**.
- **P29-T01:** Analytics REST contract audited/frozen in this plan (docs only).
- **P29-T02:** six Admin Analytics REST endpoints + ITs; AOV overview-only; inventory embed via P26 summary; **P29 complete**.
- **P30-T01:** Analytics dashboard UI contract audited/frozen (docs only); charting = CSS/SVG/tables.
- **P30-T02:** `/admin/analytics` shipped (six sections, URL Instant sync, no chart dep); **P30 complete**.
- **P31-T01:** Smoke + docs + final verification — **DONE**; Inventory + Analytics roadmap **COMPLETE**.

## P31-T01 — Final verification (2026-11-01)

### Scope confirmed (no contract/formula changes)

| Area | Delivered |
|------|-----------|
| Inventory API | `GET /admin/inventory`, `GET /admin/inventory/summary`, existing absolute `PUT .../products/{id}/inventory` |
| Inventory UI | `/admin/inventory` — summary, search, stock filters, pagination, absolute adjust; reserved read-only |
| Analytics shared | `AdminAnalyticsService` + `AnalyticsMetrics` / `AnalyticsDateRange` (chat + REST) |
| Analytics REST | Six GETs under `/api/v1/admin/analytics/**` + overview embeds inventory summary |
| Analytics UI | `/admin/analytics` — independent section queries; CSS/SVG/tables; exclusive Instant `to` |

### Semantics (frozen; verified in ITs + smoke)

- Window: `placed_at >= from AND placed_at < to` (UTC Instants; exclusive `to`).
- Revenue: `REVENUE_STATUSES` = PAID/PROCESSING/SHIPPED/COMPLETED; AOV = revenue/revenueOrderCount (overview only).
- Low-stock threshold: **`aptech.inventory.low-stock-threshold`** default **5** (inventory summary + overview embed). Chat AI threshold key remains separate.
- Category sales: **live** `products.category_id` — historical category attribution **not** preserved.
- Promotions: discount totals + breakdown from **`order_promotion_snapshots`** (ADR-020); names survive campaign delete.
- No mock analytics/inventory rows in production paths.

### Smoke

- Script: `scripts/smoke-inventory-analytics.sh`
- Make: `make smoke-inventory-analytics` (opt-in; needs stack + `DEMO_ADMIN_*` / `DEMO_CUSTOMER_*`)
- **2026-11-01 result:** **OK** after rebuilding outdated `api`/`web` images (`compose up -d` recreate; **volumes kept**).
- Covered: SPA deep links `/admin/inventory` + `/admin/analytics`; admin auth; customer 403 / anon 401; inventory summary (threshold=5, total=48); list `stockStatus=LOW` + `q=FOOD`; absolute PUT; negative PUT 400; six analytics endpoints + inverted range 400 + limit>20 400; timeseries 7 day buckets.
- **Skipped (documented):** below-reserved PUT on smoke pick (reserved=0) — covered by `AdminProductInventoryIntegrationTest`. Interactive browser clicks for filters/retry not automated — FE Vitest + API smoke cover behavior.

### Automated verification (exact)

| Command | Result |
|---------|--------|
| `cd backend && ./mvnw test` | **345** tests; **1 failure** `CatalogCacheIntegrationTest.productDetailCacheMissThenHitSkipsDb` (null assert). Inventory/analytics ITs green. `PromotionCacheIntegrationTest` green this run. |
| Re-run `./mvnw -Dtest=CatalogCacheIntegrationTest,PromotionCacheIntegrationTest test` ×2 | **BUILD SUCCESS** both (7/0) → classified **verified flake**, not inventory/analytics regression. Suite **not** claimed clean. |
| `cd frontend && npm run typecheck` | **EXIT 0** |
| `cd frontend && npm test` | First full run: 2 intermittent fails (`ProductCard`, `AdminAnalyticsPage` empty copy) → focused re-run ×2 **16/16**; second full run **269/269** PASS. |
| `cd frontend && npm run build` | **EXIT 0** (Vite production build) |

### Known gaps

- Full BE suite historically flaked on Redis/Testcontainers cache ITs (`CatalogCacheIntegrationTest`, `PromotionCacheIntegrationTest`) when Lettuce blipped and `CacheErrorHandler` swallowed PUTs.
- **P32-T01:** CatalogCache IT hardened (wait strategy, empty password, Redis PING, re-drive before assert). Full suite **345/0** after fix. PromotionCache may still share the Redis-blip class of flake.
- Playwright E2E (`make e2e-inventory-analytics`) covers Admin inventory/analytics UI headless against nginx; not a substitute for API smoke.
- No ledger / relative adjust (approved defer).

## P32-T01 — Verification hardening (2026-11-01)

### Browser E2E

| Item | Choice |
|------|--------|
| Prior framework | **None** (no Playwright/Cypress; Vitest=jsdom only) |
| Tradeoff | Playwright = real Chromium against nginx/`DEMO_*` (matches smoke auth) vs Cypress (heavier) vs Vitest browser (component-oriented, not stack E2E). Added minimal `@playwright/test` + Chromium-only install. |
| Command | `make e2e-inventory-analytics` → `scripts/e2e-inventory-analytics.sh` |
| Coverage | Customer Access denied; Admin inventory search/filter + Set on hand sheet; Admin analytics sections + date Apply → exclusive Instant `to` |
| Result | **3/3 PASS** (executed 2026-11-01 against `WEB_PORT=8088`) |

### CatalogCache flake

- **Symptom:** `productDetailCacheMissThenHitSkipsDb` null `cache.get` under full suite.
- **Cause:** transient Testcontainers Redis/Lettuce disconnect; production `CacheErrorHandler` logs+swallows PUT → entry never stored; not a catalog formula regression.
- **Fix (test-only):** Redis ready wait; force `spring.data.redis.password=""`; direct `PING`/`SET`/`GET` probe; `requireProductDetailCached` re-PING + one miss→put before asserting (still requires non-null entry + zero Hibernate statements on hit).

### FE flakes (first full Vitest run in P31)

| Test | Cause | Fix |
|------|-------|-----|
| `ProductCard` add-to-cart enabled | Asserted enabled before AuthProvider left `booting` | `waitFor(() => expect(addButton).toBeEnabled())` |
| `AdminAnalyticsPage` empty product copy | Overview URL Instant sync remounted queries mid-assert | Seed route with empty window Instants; `waitFor` both empty notices |
