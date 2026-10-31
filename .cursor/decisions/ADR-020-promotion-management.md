# ADR-020: Promotion management domain, stacking, and checkout discounts

- **Status:** Accepted
- **Date:** 2026-10-31
- **Accepted:** 2026-10-31
- **Related tasks:** P18–P23 (see `.cursor/plans/09-promotion-management.md`)

## Context

The storefront homepage currently hardcodes FoodMart banner assets. Checkout prices are server-authoritative (`subtotal + shipping(0)`) with **no** discount fields, coupons, or promotion tables. Product image upload stores filesystem paths only under `/uploads/products/**`. The project uses a modular monolith with **no `jsonb` columns** and Liquibase through `130-product-image-url` (existing changelogs must not be rewritten).

We need Admin-configurable promotion strategies, campaign lifecycle/schedules, homepage placements (hero carousel + stacked promos), and checkout discounts that never trust the client — while remaining backward-compatible with existing orders and “no active campaign” checkout behavior.

## Decision

### D1 — Entity distinction and relationships

| Entity | Role | Owns |
|--------|------|------|
| **PromotionStrategy** | Reusable **pricing/marketing rule template** | `type`, discount value columns, min order/qty, default `priority` / `stackable`, `active` |
| **PromotionCampaign** | **Scheduled instance** of a strategy that can go live | FK `strategy_id`, schedule window, **authoritative** `priority` / `stackable` for runtime, manual `status`, eligibility joins, name/slug |
| **PromotionPlacement** | **Storefront creative** for a campaign | placement key, copy, CTA, image paths, display order, optional tighter window |

Relationships:

```
PromotionStrategy (1) ──< (0..n) PromotionCampaign
PromotionCampaign (1) ──< (0..n) PromotionPlacement
PromotionCampaign (1) ──< (0..n) eligibility product/category rows
ShopOrder (1) ──< (0..n) OrderPromotionSnapshot
```

- A strategy may have zero campaigns (library only).
- A campaign always references exactly one strategy (`ON DELETE RESTRICT`).
- Placements always belong to one campaign (`ON DELETE CASCADE`).
- `BANNER_ONLY` strategies exist so campaigns can ship placements **without** pricing side effects.
- Pricing campaigns may also have placements (marketing + discount).

**Runtime ownership:** checkout and public placement APIs evaluate **campaigns** (with strategy type/values joined). Strategy `priority` / `stackable` are **defaults copied into the campaign on create**; later strategy edits do **not** rewrite existing campaigns’ priority/stackable (Admin edits campaign explicitly).

### D2 — Deterministic MVP stacking and priority

**Clock:** `Instant.now()` (UTC). Window is **inclusive**: `starts_at <= now <= ends_at`.

**Candidate set (pricing):** campaigns where all hold:

1. `status == ACTIVE`
2. `starts_at <= now <= ends_at`
3. strategy `active == true`
4. strategy `type != BANNER_ONLY`
5. eligibility + `min_order_amount` / `min_quantity` pass against the cart

**Sort key (stable):** `campaign.priority DESC`, then `campaign.starts_at ASC`, then `campaign.id ASC`.

**Base amounts (per campaign, before stacking interaction):**

| Strategy type | Eligible base |
|---------------|---------------|
| `PRODUCT_DISCOUNT` | Σ lineSubtotal for lines whose `productId` is in campaign product set |
| `CATEGORY_DISCOUNT` | Σ lineSubtotal for lines whose `categoryId` is in campaign category set |
| `PERCENTAGE_DISCOUNT` | Σ lineSubtotal for lines matching eligibility (empty sets → all lines) |
| `FIXED_AMOUNT_DISCOUNT` | same eligible base as percentage (cap applies to computed off) |
| `BANNER_ONLY` | **excluded** from pricing entirely |

**Per-campaign discount computation:**

- Percent path: `raw = eligibleBase × percent / 100` (scale 2, `HALF_UP`).
- Fixed path: `raw = min(discount_amount, eligibleBase)`.
- Apply `max_discount_amount` if set: `campaignDiscount = min(raw, max_discount_amount)`.
- If `campaignDiscount <= 0`, skip (does not “consume” a non-stackable slot).

**Combining / stacking:**

1. Partition candidates into `stackable=true` and `stackable=false` (campaign flag).
2. **Non-stackable:** walk sorted list; apply the **first** campaign that yields `campaignDiscount > 0`; ignore remaining non-stackable.
3. **Stackable:** walk sorted list; apply **every** campaign that yields `campaignDiscount > 0`.
4. Each campaign’s `campaignDiscount` is computed from the **original pre-discount eligible base** for that campaign (non-compounding). Same-priority campaigns are already ordered by `starts_at` then `id` — no random choice.
5. `order.discount_amount = min(Σ campaignDiscount, order.subtotal_amount)`.
6. `order.total_amount = subtotal + shipping - discount` (shipping remains 0 in MVP).

**Banner-only:** never enters the candidate set; placements still render when placement-effective.

**Checkout campaign selection:** the backend alone determines applicable campaigns. There is **no** client `preferCampaignIds` (or similar) on checkout.

### D3 — Order promotion snapshot (historical pricing)

Even if a campaign/strategy is later edited or deleted, the order must retain enough denormalized data to explain the paid total.

**On `orders` (new columns via additive migration only):**

| Column | Behavior |
|--------|----------|
| `discount_amount` | `NUMERIC(12,2) NOT NULL DEFAULT 0` — existing rows get 0 |
| `promotion_summary` | `VARCHAR(255) NULL` — short joined label at checkout time |

**On `order_promotion_snapshots` (one row per applied pricing campaign):**

| Field | Purpose |
|-------|---------|
| `id` | UUID PK |
| `order_id` | FK CASCADE |
| `campaign_id` | UUID NULL, `ON DELETE SET NULL` — soft reference |
| `strategy_id` | UUID NULL, `ON DELETE SET NULL` |
| `campaign_name` | denormalized name at apply time |
| `campaign_slug` | denormalized slug at apply time |
| `strategy_name` | denormalized |
| `strategy_type` | enum string at apply time |
| `strategy_code` | nullable denormalized |
| `priority` | campaign priority used |
| `stackable` | flag used |
| `discount_percent` | nullable copy of rule value used |
| `discount_amount_rule` | nullable fixed-rule value used (not the applied money) |
| `max_discount_amount` | nullable cap used |
| `eligible_subtotal` | base used for this campaign |
| `discount_amount` | money actually applied for this campaign |
| `currency` | order currency |
| `applied_at` | TIMESTAMPTZ |

**Order-level discount only (MVP):** promotions adjust order `discount_amount` / snapshots. Do **not** persist per-line discount allocations on `order_items`. Line `unit_price` / `line_total` remain catalog price snapshots.

Do **not** mutate historical snapshots when Admin edits live campaigns (including strategy changes on ACTIVE campaigns).

### D4 — Backward-compatible migrations

1. **Never modify** existing Liquibase files `000`–`130` (or any already-shipped changeset).
2. Add only new files `140+` and register them in `db.changelog-master.yaml`.
3. `orders.discount_amount NOT NULL DEFAULT 0` so existing orders migrate to `0` without backfill scripts.
4. When **no** pricing-effective campaign matches, checkout behavior equals today’s math: `discount_amount = 0`, `total = subtotal + shipping`, no snapshot rows.
5. Reservation / confirm / release / idempotency / order status machine unchanged (ADR-006/007/008).

### D5 — Banner upload reuse

1. **Reuse** existing secure upload pipeline: multipart Admin auth, size/type validation (JPEG/PNG/WebP, size cap), UUID filename, filesystem volume, **path-only** in PostgreSQL — same conventions as product images.
2. **Isolate** storage prefixes: `/uploads/products/` vs `/uploads/promotions/` (separate dirs + path-rule regexes). Product entities must reject promotion paths and vice versa.
3. **Do not** store image bytes in PostgreSQL.
4. **Shared endpoint with purpose:** `POST /api/v1/admin/uploads/images?purpose=PRODUCT|PROMOTION` (default `PRODUCT` for backward compatibility). One upload core parameterized by `UploadPurpose`; thin purpose-specific path rules / public mounts. No separate promotion-only upload controller body.
5. Public `GET /uploads/promotions/**` permitted like products.

### D6 — Campaign lifecycle and scheduling

**Manual `status`:** `DRAFT` | `SCHEDULED` | `ACTIVE` | `PAUSED` | `EXPIRED`

| Status | Pricing-effective? | Placement-effective? | Notes |
|--------|--------------------|----------------------|-------|
| `DRAFT` | No | No | editable freely |
| `SCHEDULED` | No | No | waiting for Admin **Activate**; never auto-promoted at `starts_at` |
| `ACTIVE` | Only if `starts_at <= now <= ends_at` | Same + placement enabled/window | may be activated **before** `starts_at` |
| `PAUSED` | No | No | Admin pause; resume via Activate when not past `ends_at` |
| `EXPIRED` | No | No | lifecycle maintenance via expiry job when `now > ends_at` |

**Manual activation only (R1):**

- There is **no** automatic DB status transition at `starts_at`.
- Admin may set `status = ACTIVE` while `starts_at` is still in the **future**. The row stays `ACTIVE`; **effective** pricing/placement is computed as `status == ACTIVE` **and** `starts_at <= now <= ends_at` (plus strategy/placement rules). Until `starts_at`, the campaign is not effective even though status is `ACTIVE`.
- `SCHEDULED` is an Admin workflow state only; it does not become effective without Activate.

**Boundaries:**

- Require `ends_at > starts_at` on write.
- Inclusive window: effective while `starts_at <= now <= ends_at`.
- Activate rejected if `now > ends_at`.
- Schedule action → `SCHEDULED` when preparing a future campaign that is not yet activated.

**Timezone:** all API timestamps are **UTC** (`TIMESTAMPTZ` / ISO-8601 with `Z`). Server evaluation uses UTC clock. Admin UI may display local time but must submit UTC.

**Edit while `ACTIVE` (MVP), including strategy change (R4):**

- Allowed for: name/notes, priority, stackable, eligibility, placements, `starts_at`/`ends_at`, and **`strategy_id`**.
- Changing `strategy_id` on an ACTIVE campaign is allowed. Historical order snapshots are preserved unchanged. Subsequent checkout and public placement evaluation **revalidate** the campaign against the new strategy configuration (type, values, strategy `active`).
- Changes apply to **future** checkouts and public reads immediately after commit (no campaign versioning).
- Narrowing the window such that `now` falls outside → immediately non-effective while `status` may remain `ACTIVE` until the expiry job sets `EXPIRED` when `now > ends_at`.
- Extending `ends_at` while `ACTIVE` is allowed.
- Soft-deactivating a strategy (`active=false`) immediately removes pricing/placement effectiveness for dependent campaigns even if campaign `status` stays `ACTIVE`.

**Expiry job (R9):**

- MVP **includes** a background job that may set `ACTIVE → EXPIRED` when `now > ends_at` (lifecycle state maintenance / Admin list hygiene).
- **Runtime eligibility is authoritative and independent of the job.** Checkout and public placement APIs must enforce time boundaries (`starts_at`/`ends_at`) and status rules even if the job has not yet run (an `ACTIVE` row past `ends_at` is still not effective).

### D7 — Package / API surface

- Package: `com.aptech.aptech4.promotion`.
- Admin mutations: `/api/v1/admin/promotions/**`.
- Public reads: `/api/v1/promotions/**`.
- Checkout remains `/api/v1/checkout`; response gains `discountAmount` + `appliedPromotions` (no prefer-campaign request field).

### D8 — Homepage empty placements (R3)

When no placement-effective creatives exist for a homepage slot, the storefront uses **static FoodMart artwork** as fallback (existing bundled banner assets). Do not leave broken empty chrome without fallback in MVP.

### D9 — Public placement cache (R7)

Cache public homepage / placement responses with a **short TTL** as a safety net. **Must invalidate** on Admin mutations that can change what the storefront shows, including at least:

- placement create / update / delete
- campaign create / update / delete
- campaign activate / pause / schedule (status transitions)
- campaign eligibility updates that Admin surfaces with placements (invalidate with campaign mutations)
- strategy activate/deactivate when it can remove placement-effectiveness of dependent campaigns

Do **not** rely on TTL alone for correctness after Admin edits.

### D10 — Client scope (R8)

MVP covers **Admin Web** and **Storefront Web** only. Flutter promotion/homepage/checkout discount parity is **deferred**.

### D11 — Finalized product decisions R1–R9

| ID | Decision |
|----|----------|
| **R1** | Manual activation only. No automatic activation at campaign start. `ACTIVE` + future `starts_at` is allowed; effectiveness is computed from status ∧ time window (no DB flip at start). |
| **R2** | Omit `preferCampaignIds` from checkout. Backend determines applicable campaigns. |
| **R3** | Static FoodMart artwork as homepage fallback when no active placements are available. |
| **R4** | Allow changing strategy of an ACTIVE campaign. Preserve historical order snapshots. Revalidate updated configuration at runtime. |
| **R5** | Order-level discount only. No line-level discount persistence on `order_items`. |
| **R6** | Shared upload endpoint with explicit `purpose` parameter. Product and promotion paths isolated. |
| **R7** | Short-TTL cache for homepage placements **plus** invalidation on create/update/activate/pause/delete (and related mutations). TTL is fallback only. |
| **R8** | Defer Flutter parity. MVP = Admin Web + Storefront Web. |
| **R9** | Include expiry job in MVP for lifecycle maintenance. Checkout/public APIs independently enforce time boundaries and must not depend on the job. |

R1–R9 are **accepted** with this ADR. No further approval gate remains for these items.

## Alternatives considered

1. **Inline strategy fields on campaign only** — rejected (Admin requires strategy CRUD/reuse).
2. **jsonb strategy_config** — deferred (first jsonb in repo; relational sufficient for MVP types).
3. **Auto-activate at `starts_at`** — rejected (R1: manual Activate only; effectiveness from status ∧ window).
4. **Promo codes** — out of scope.
5. **Reuse `/uploads/products/` for banners** — rejected (asset isolation).
6. **Compounding stackable discounts** — rejected (non-compounding eligible base is deterministic and easier to audit).
7. **Require pause before any active edit** — rejected for MVP velocity; mitigated by order snapshots (R4 allows ACTIVE strategy change).
8. **Client preferCampaignIds** — rejected (R2).
9. **Line-level discount columns on order_items** — rejected for MVP (R5).
10. **Separate promotion upload endpoint implementation** — rejected (R6: shared endpoint + purpose).
11. **TTL-only placement cache** — rejected (R7: invalidate on mutations).
12. **Flutter in MVP** — deferred (R8).
13. **Defer expiry job** — rejected (R9: include job; runtime checks remain authoritative).

## Consequences

- Positive: Clear Strategy/Campaign/Placement boundaries; deterministic stacking; historical totals survive campaign edits/deletes; additive Liquibase; shared upload with isolated paths; UTC schedules; explicit R1–R9; cache correctness via invalidation; checkout independent of expiry job.
- Negative / trade-offs: Live ACTIVE edits (including strategy changes) affect new checkouts immediately; cart UI still shows undiscounted catalog prices in MVP; Flutter deferred; Admin may see `ACTIVE` rows that are not yet/no longer effective until the expiry job runs.
- Follow-ups: Implement P18–P23 per `.cursor/plans/09-promotion-management.md`; update `00-master-plan.md` / `TASKS.md` when coding starts.

## Acceptance criteria (ADR-level)

1. Docs name Strategy vs Campaign vs Placement roles and FKs as above.
2. Stacking algorithm is fully specified (sort, non-stackable cap of one, stackable accumulate, non-compounding, max caps, banner exclusion).
3. Snapshot field list is sufficient after campaign/strategy delete (`campaign_id` null-safe + denormalized rule fields); order-level discount only.
4. Migration plan is additive `140+` only; existing orders default `discount_amount = 0`; no-campaign checkout equals legacy totals.
5. Upload reuse via shared endpoint + `purpose`; path isolation; no PG bytes.
6. Lifecycle: manual Activate; `ACTIVE`+future start allowed; inclusive UTC windows; ACTIVE strategy change allowed with immutable snapshots; expiry job present but non-authoritative for eligibility.
7. Homepage fallback uses static FoodMart art; placement cache invalidates on listed mutations.
8. R1–R9 recorded as accepted; MVP is Admin Web + Storefront Web only.
