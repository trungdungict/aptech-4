# P18–P23 — Promotion Management (strategies, campaigns, placements, checkout)

## Goal

Deliver an Admin-configurable Promotion Management module that powers:

1. Homepage hero carousel + stacked promo banners (CMS-driven, not hardcoded).
2. Pricing strategies applied at checkout with server-authoritative totals and order snapshots.
3. Campaign lifecycle, schedules, priorities, stacking rules, and placement targeting.

Inspired by a grocery-style homepage layout (large left hero carousel + two right stacked banners with title / offer / CTA), but scoped as a full promotion domain — not banners alone.

**ADR-020 is Accepted** (2026-10-31), including finalized R1–R9. Implementation may proceed when P18 tasks are scheduled.

**Normative stacking / lifecycle / snapshot / upload / migration / R1–R9 rules live in** [ADR-020](../decisions/ADR-020-promotion-management.md). This plan operationalizes them.

## Prerequisites

| Dependency | Status / evidence |
|------------|-------------------|
| Catalog products/categories + Admin CRUD | Done (P05) |
| Cart + inventory reservation + checkout (ADR-006/007/008) | Done (P06) |
| Secure product image upload (path-only in PG, `/uploads/products/**`) | Done |
| Admin React patterns (lazy routes, RHF/Zod/TanStack Query) | Done (P10) |
| Liquibase through `130-product-image-url` | Done |
| No existing coupon/discount/tax fields | Confirmed — greenfield money fields |

## Phase map

| Phase | Name | Depends on |
|-------|------|------------|
| **P18** | Domain model, Liquibase (ADR-020 Accepted) | — |
| **P19** | Admin + public promotion APIs | P18 |
| **P20** | Storefront homepage placements UI | P19 (public read) |
| **P21** | Admin promotion UI | P19 |
| **P22** | Checkout pricing + order snapshots + expiry job | P18–P19 |
| **P23** | Tests, demo seed, docs, QA | P19–P22 |

Update `.cursor/plans/00-master-plan.md` and `.cursor/state/TASKS.md` when implementation starts (not in this planning task).

---

## 1. Current architecture findings

### Backend

- Modular monolith packages: `identity`, `catalog`, `cart`, `order`, `notification`, `chat` (`ADR-001`).
- **No promotion/coupon domain today.** Cart/checkout assume no discounts.
- Checkout (`CheckoutService`): server re-reads `Product.unitPrice`;  
  `lineTotal = unitPrice × qty`; `shippingAmount = 0`; `totalAmount = subtotal + shipping`.  
  Request body: `idempotencyKey`, `addressId`, `simulateFailure` only — **never trust client money**.
- Order snapshots: `orders` (`subtotal_amount`, `shipping_amount`, `total_amount`) + `order_items` (sku/name/unit_price/qty/line_total). No `discount_amount`.
- Reservation flow must remain: reserve → mock pay → confirm/release (ADR-006/007).
- Liquibase: `changes/NNN-slug.yaml`; latest **`130-product-image-url`**. **No `jsonb`**. Existing `000`–`130` must not be edited.
- Security: `/api/v1/admin/**` → `ROLE_ADMIN`; public catalog GETs permitAll.
- Image upload: `ProductImageUploadService` + `ProductImagePathRules` → filesystem → DB path `/uploads/products/{uuid}.ext` only.

### Frontend

- `HomePage.tsx` hardcodes FoodMart banner paths; decorative promos are not priced promotions.
- Admin feature folders: `api/` + `pages/` + `schemas.ts` + query keys + Vitest `fetch` mocks.

### Implications

- New package `com.aptech.aptech4.promotion`.
- Additive order discount columns + snapshot table.
- Homepage placements from public APIs; catalog sections stay live catalog queries.
- Relational config (no jsonb in MVP).

---

## 2. Domain model — Strategy vs Campaign vs Placement

### 2.1 Distinction (normative)

| Concept | What it is | What it is not |
|---------|------------|----------------|
| **PromotionStrategy** | Reusable **rule template**: type + discount math knobs + mins + default priority/stackable | Not scheduled; not shown on the homepage by itself |
| **PromotionCampaign** | **Go-live instance**: binds a strategy to a time window, status, eligibility, and runtime priority/stackable | Not the creative image/CTA (those are placements) |
| **PromotionPlacement** | **Storefront creative** attached to a campaign (hero/promo slots) | Not a pricing engine input (`BANNER_ONLY` campaigns use these only) |

```
PromotionStrategy (1) ──< (0..n) PromotionCampaign
PromotionCampaign (1) ──< (0..n) PromotionPlacement
PromotionCampaign (1) ──< (0..n) PromotionCampaignProduct
PromotionCampaign (1) ──< (0..n) PromotionCampaignCategory
ShopOrder (1) ──< (0..n) OrderPromotionSnapshot
```

**Field ownership**

- Strategy owns: `type`, `discount_percent` / `discount_amount`, `max_discount_amount`, `min_order_amount`, `min_quantity`, `active`, default `priority`/`stackable`.
- Campaign owns: `strategy_id`, `status`, `starts_at`/`ends_at`, **runtime** `priority`/`stackable` (copied from strategy on create, then independent), eligibility sets, name/slug.
- Placement owns: `placement_key`, copy, CTA, image URLs, `display_order`, optional placement window, `enabled`.

### 2.2 Tables (summary)

**`promotion_strategies`** — see ADR-020 D1; types: `PERCENTAGE_DISCOUNT`, `FIXED_AMOUNT_DISCOUNT`, `PRODUCT_DISCOUNT`, `CATEGORY_DISCOUNT`, `BANNER_ONLY` (defer `BUY_X_GET_Y`, `FREE_SHIPPING`).

**`promotion_campaigns`** — FK strategy `ON DELETE RESTRICT`; status enum; `ends_at > starts_at`.

**Eligibility** — product/category join tables. Empty both sets ⇒ all lines for order-level `%`/fixed; `PRODUCT_DISCOUNT` / `CATEGORY_DISCOUNT` require matching join rows.

**`promotion_placements`** — keys `HOME_HERO` | `HOME_PROMO_TOP` | `HOME_PROMO_BOTTOM`; image paths under `/uploads/promotions/` only.

### 2.3 Acceptance criteria — entity model

- [ ] AC-E1: Creating a strategy does not create campaigns or placements.
- [ ] AC-E2: Creating a campaign requires an existing strategy; deleting a strategy with campaigns is rejected (`RESTRICT`).
- [ ] AC-E3: Deleting a campaign cascades placements + eligibility rows.
- [ ] AC-E4: Campaign create copies strategy default priority/stackable; later strategy priority edits do not rewrite campaigns.
- [ ] AC-E5: `BANNER_ONLY` strategy rejects non-null discount value fields on write.

### 2.4 Tests — entity model

- Unit/IT: strategy CRUD validation by type; campaign FK restrict; placement cascade; default copy of priority/stackable; BANNER_ONLY value rejection.

---

## 3. Deterministic stacking and priority (MVP)

Normative algorithm: **ADR-020 D2**. Operational summary:

1. Build pricing candidates (ACTIVE + inclusive UTC window + strategy active + not `BANNER_ONLY` + eligibility/mins).
2. Sort: `priority DESC`, `starts_at ASC`, `id ASC`.
3. Compute each campaign’s discount from **pre-discount eligible base** (product/category/order-level rules per type).
4. Cap per campaign with `max_discount_amount`; skip if ≤ 0.
5. Apply **at most one** non-stackable (first successful); apply **all** successful stackable; non-compounding.
6. `order.discount_amount = min(sum, subtotal)`; `total = subtotal + shipping - discount`.

### 3.1 Combining rules (explicit)

| Question | MVP answer |
|----------|------------|
| Can discounts combine? | Yes **only** when `campaign.stackable == true`. Non-stackable campaigns never combine with each other. |
| Stackable + non-stackable together? | Yes: one non-stackable **plus** any number of stackable (both groups use the same sort order within their partition). |
| Same priority? | Tie-break `starts_at ASC`, then `id ASC` — deterministic, no random. |
| Banner-only? | Never in pricing candidate set; no effect on totals. |
| Max discount? | Per-campaign `max_discount_amount`, then global cap at subtotal. |

### 3.2 Acceptance criteria — stacking

- [ ] AC-S1: Two non-stackable ACTIVE campaigns → exactly one applied (higher priority; tie → earlier `starts_at`, then lower `id`).
- [ ] AC-S2: Two stackable campaigns → both applied; sum equals sum of individually computed discounts (non-compounding bases).
- [ ] AC-S3: One non-stackable + one stackable → both can apply (when each yields > 0).
- [ ] AC-S4: `PRODUCT_DISCOUNT` ignores non-matching lines; `CATEGORY_DISCOUNT` uses category membership.
- [ ] AC-S5: Order-level `%` / fixed with empty eligibility applies to full subtotal.
- [ ] AC-S6: `max_discount_amount` truncates a single campaign’s contribution.
- [ ] AC-S7: Total discount never exceeds subtotal; never negative.
- [ ] AC-S8: `BANNER_ONLY` ACTIVE campaign with placements does not change `discount_amount` / `total_amount`.
- [ ] AC-S9: Same priority, different `starts_at` → earlier start wins the non-stackable slot.

### 3.3 Tests — stacking

- Unit: `PromotionPricingService` table-driven cases for AC-S1…S9.
- IT: checkout with seeded overlapping campaigns; assert `appliedPromotions` order and amounts; payment charged for final total.

---

## 4. Order promotion snapshot fields

Normative field list: **ADR-020 D3**.

**Order columns (additive):** `discount_amount NOT NULL DEFAULT 0`, `promotion_summary VARCHAR(255) NULL`.

**`order_promotion_snapshots`:** denormalized campaign/strategy identity + rule values + `eligible_subtotal` + applied `discount_amount` + `applied_at`; `campaign_id` / `strategy_id` nullable `ON DELETE SET NULL`.

### 4.1 Acceptance criteria — snapshots

- [ ] AC-H1: After checkout with promotions, snapshot row count equals applied pricing campaigns.
- [ ] AC-H2: Sum of snapshot `discount_amount` equals `orders.discount_amount`.
- [ ] AC-H3: After Admin renames campaign / changes percent / deletes campaign, order GET still returns prior snapshot names/amounts; `campaign_id` may become null after delete.
- [ ] AC-H4: Orders placed before feature (migrated) have `discount_amount = 0` and zero snapshot rows.
- [ ] AC-H5: `order_items` unit prices remain catalog snapshots; MVP does **not** persist line-level discount on `order_items` (order-level `discount_amount` + snapshots only — R5).
- [ ] AC-H6: Changing `strategy_id` on an ACTIVE campaign does not alter prior order snapshots (R4).

### 4.2 Tests — snapshots

- IT: checkout → mutate/delete campaign → re-fetch order → historical fields intact.
- IT: migrate existing fixture order → `discount_amount = 0`.

---

## 5. Backward-compatible migration behavior

Normative: **ADR-020 D4**.

| Rule | Detail |
|------|--------|
| Do not edit | Any existing changeset `000`–`130` |
| Add only | `140-promotion-strategies.yaml` … `144-order-promotion-discount.yaml` (+ master includes) |
| Existing orders | `discount_amount` defaults to `0` via column default |
| No active campaign | Checkout math identical to pre-feature (`discount=0`, same total) |
| Inventory/checkout | Reservation, locks, idempotency, status graph unchanged |

### 5.1 Acceptance criteria — migration / compat

- [ ] AC-M1: Fresh DB applies `000`–`144` cleanly.
- [ ] AC-M2: DB that already has `130` applies only new `140+` changesets.
- [ ] AC-M3: Pre-existing order rows show `discount_amount = 0` after `144`.
- [ ] AC-M4: CheckoutIntegrationTest scenarios with **no** campaigns still pass with same totals (plus new fields = 0 / empty list).
- [ ] AC-M5: Git diff of `changes/010`–`130` is empty for this feature.

### 5.2 Tests — migration / compat

- Liquibase IT / existing suite green; explicit assert `discountAmount == 0` and empty `appliedPromotions` when none active.
- Optional: checksum test or CI check that historical changelog files are untouched.

---

## 6. Promotion banner upload reuse

Normative: **ADR-020 D5**.

| Concern | Approach |
|---------|----------|
| Validation | Reuse type/size/magic checks from product upload |
| Storage | Same volume root pattern; **subdir** `promotions/` vs `products/` |
| DB | Path string only (`/uploads/promotions/{uuid}.ext`) |
| Code | Shared upload core + purpose enum; thin path-rule classes |
| Security | Admin multipart only; public GET for `/uploads/promotions/**` |
| Isolation | Product `imageUrl` validator rejects `/uploads/promotions/`; placement validators reject `/uploads/products/` |
| HTTP (R6) | Shared `POST /api/v1/admin/uploads/images?purpose=PRODUCT\|PROMOTION` (default `PRODUCT`) |

### 6.1 Acceptance criteria — upload

- [ ] AC-U1: Admin can upload with `purpose=PROMOTION` and receive a `/uploads/promotions/…` path.
- [ ] AC-U2: Oversized / GIF / non-image rejected with same class of errors as product upload.
- [ ] AC-U3: Placement create/update rejects product upload paths.
- [ ] AC-U4: Product update rejects promotion upload paths.
- [ ] AC-U5: No image bytes columns added to PostgreSQL.
- [ ] AC-U6: Upload implementation shares validation/write helpers (no forked copy-paste service body); purpose selects path prefix only.

### 6.2 Tests — upload

- Extend/adapt `ProductImageUpload*` tests for purpose=PROMOTION.
- Path-rules unit tests for both prefixes.
- Admin placement IT: bad path → 400.

---

## 7. Campaign lifecycle and scheduling

Normative: **ADR-020 D6**.

### 7.1 Status matrix

| Manual status | Pricing-effective | Placement-effective |
|---------------|-------------------|---------------------|
| DRAFT | No | No |
| SCHEDULED | No | No (workflow only; R1: no auto-activate) |
| ACTIVE | Yes iff `starts_at <= now <= ends_at` | Yes iff window + placement enabled/(optional placement window) |
| PAUSED | No | No |
| EXPIRED | No | No |

**Manual activation (R1):** Admin Activate sets `status = ACTIVE`. A campaign may be activated with a **future** `starts_at`. No database status transition is required at `starts_at`; **effective** runtime status is always calculated from configured `status` ∧ time window (and strategy/placement rules). `SCHEDULED` never becomes effective without Activate.

**Timezone:** UTC everywhere (`TIMESTAMPTZ`, ISO-8601 `Z`). Server uses `Instant.now()`.

**Inclusive bounds:** effective on both `starts_at` and `ends_at` instants.

**Edit while ACTIVE (R4):** allowed, including changing `strategy_id`; affects future checkouts/public reads immediately; never rewrites past order snapshots; runtime revalidates new strategy. Soft-disable strategy ⇒ dependents non-effective immediately.

**Expiry job (R9, required in MVP):** sets `ACTIVE → EXPIRED` when `now > ends_at` for **lifecycle state maintenance** (Admin hygiene). **Runtime eligibility checks remain authoritative** — checkout and public placement APIs enforce time boundaries even if the job has not run.

### 7.2 Acceptance criteria — lifecycle

- [ ] AC-L1: DRAFT / SCHEDULED / PAUSED / EXPIRED never pricing- or placement-effective.
- [ ] AC-L2: ACTIVE before `starts_at` → not effective; at `starts_at` → effective; at `ends_at` → still effective; after `ends_at` → not effective (even if status still ACTIVE).
- [ ] AC-L3: Activate when `now > ends_at` → 400.
- [ ] AC-L4: Pause then Activate within window restores effectiveness.
- [ ] AC-L5: Edit ACTIVE percent or `strategy_id` → next checkout uses new config; prior order snapshots unchanged.
- [ ] AC-L6: API examples and Admin forms document UTC.
- [ ] AC-L7: SCHEDULED does not become ACTIVE solely because `now >= starts_at` (R1).
- [ ] AC-L8: Activate with future `starts_at` succeeds; status stays ACTIVE; not effective until window opens (no start-time DB flip).
- [ ] AC-L9: Past `ends_at` with status still ACTIVE → checkout applies no discount from that campaign; expiry job later may set EXPIRED without being required for correctness.

### 7.3 Tests — lifecycle

- IT: time-based cases via fixed `Clock` bean or controllable time source in pricing service.
- IT: activate/pause/schedule transitions + boundary rejects; ACTIVE+future start.
- IT: edit-while-active including strategy_id change + historical order immutability.
- IT: strategy `active=false` hides pricing/placements for ACTIVE campaigns.
- IT: expiry job flips status; checkout correctness without waiting for job.

---

## 8. API contracts (DTO sketches)

Base: `/api/v1`. Admin: `/api/v1/admin/promotions/**`.

### 8.1 Admin — strategies

| Method | Path |
|--------|------|
| GET/POST | `/admin/promotions/strategies` |
| GET/PUT | `/admin/promotions/strategies/{id}` |
| PATCH | `/admin/promotions/strategies/{id}/status` |

### 8.2 Admin — campaigns

| Method | Path |
|--------|------|
| GET/POST | `/admin/promotions/campaigns` |
| GET/PUT | `/admin/promotions/campaigns/{id}` |
| POST | `…/{id}/activate` \| `pause` \| `schedule` \| `preview` |
| PUT | `…/{id}/eligibility` |

Response includes `status`, `effectiveStatus`, `pricingEffective`, `placementEffective`.

### 8.3 Admin — placements + upload

| Method | Path |
|--------|------|
| CRUD | `/admin/promotions/campaigns/{id}/placements`, `/admin/promotions/placements/{id}` |
| Upload | `POST /admin/uploads/images?purpose=PROMOTION` → `/uploads/promotions/…` (R6) |

### 8.4 Public

| Method | Path |
|--------|------|
| GET | `/promotions/placements?keys=…` |
| GET | `/promotions/homepage` → `{ hero[], promoTop, promoBottom }` |

### 8.5 Checkout

Extend response only (no trusted client discount):

```json
{
  "subtotalAmount": 200000,
  "shippingAmount": 0,
  "discountAmount": 40000,
  "totalAmount": 160000,
  "appliedPromotions": [
    {
      "campaignId": "…",
      "campaignName": "…",
      "strategyType": "CATEGORY_DISCOUNT",
      "discountAmount": 40000
    }
  ]
}
```

**MVP:** backend auto-applies campaigns per ADR-020 stacking (**R2:** no `preferCampaignIds` or client campaign selection on checkout).

---

## 9. Liquibase migration plan

| File | Purpose |
|------|---------|
| `140-promotion-strategies.yaml` | strategies + checks |
| `141-promotion-campaigns.yaml` | campaigns + status/time checks |
| `142-promotion-eligibility.yaml` | product/category joins |
| `143-promotion-placements.yaml` | placements |
| `144-order-promotion-discount.yaml` | `orders.discount_amount` default 0, `promotion_summary`, `order_promotion_snapshots` |

Author `aptech4`. No seed in Liquibase. **Do not modify `010`–`130`.**

---

## 10. Frontend plan

### 10.1 Storefront (P20)

- API client + `HomeHeroCarousel` / `HomePromoStack` / `PromoBannerCard`.
- Refactor `HomePage` slots to API data; catalog sections unchanged.
- Empty/error: **static FoodMart artwork fallback** when no active placements (**R3**); non-blocking error + same fallback.
- Public placement responses: short-TTL cache + **invalidate** on campaign/placement create/update/activate/pause/delete (**R7**); TTL is fallback only.
- Vitest for loading/empty/error + CTA mapping + fallback.

### 10.2 Admin (P21)

- Routes: `/admin/promotions`, strategies CRUD, campaign edit tabs (Details / Eligibility / Placements / Preview).
- Reuse upload field pattern with promotion purpose/path rules.
- Activate/pause + validation/error handling tests.

---

## 11. Checkout pricing integration

`PromotionPricingService` inside `CheckoutService` after line snapshot, before payment (ADR-020 D2). Persist discount + snapshots; charge final total. Preserve reservation/idempotency/failure release.

**Non-goals MVP:** promo codes, cart live estimates, BUY_X_GET_Y / FREE_SHIPPING, Flutter UI (**R8** deferred).

---

## 12. Security and validation

| Rule | Enforcement |
|------|-------------|
| Admin mutations | `/api/v1/admin/**` |
| Public GETs | no draft/paused/expired/out-of-window leakage |
| Money | server-only; ignore client discount fields |
| Images | purpose-isolated paths; no PG bytes |
| CTA | validate PRODUCT/CATEGORY FKs; URL allowlist |
| RBAC tests | CUSTOMER/anon 401/403 |

---

## 13. Automated test matrix (consolidated)

| Decision area | Must-have tests |
|---------------|-----------------|
| Entity roles | AC-E1…E5 |
| Stacking | AC-S1…S9 (+ checkout IT) |
| Snapshots | AC-H1…H5 |
| Migration/compat | AC-M1…M5 |
| Upload | AC-U1…U6 |
| Lifecycle | AC-L1…L7 |
| Admin auth | CUSTOMER/anon forbidden on mutations |
| Public placements | filter + ordering |
| FE Admin | form validation + activate/pause + API errors |
| FE Home | hero/promo states + CTA |
| E2E/smoke | Admin banner → public homepage; Admin % → checkout discount |

Boundary suite: future window, expired, paused, invalid %, empty PRODUCT eligibility → 400, overlapping same priority, strategy deactivated under ACTIVE campaign.

---

## 14. Phased tasks

### P18 — Schema + domain + ADR

| ID | Task | Depends |
|----|------|---------|
| P18-T01 | Confirm ADR-020 Accepted + R1–R9 in TASKS checkpoint | — (docs done) |
| P18-T02 | Liquibase 140–144 only (no edits to ≤130) | P18-T01 |
| P18-T03 | JPA entities/repos in `promotion.*` | P18-T02 |
| P18-T04 | Order discount columns + snapshot entity (order-level only, R5) | P18-T02 |
| P18-T05 | Shared upload `?purpose=` + `/uploads/promotions/**` security (R6) | P18-T02 |

**AC P18:** migrate OK; entities compile; ADR-020 Accepted; no behavior change without feature data.

### P19 — APIs

| ID | Task | Depends |
|----|------|---------|
| P19-T01…T07 | Strategy/campaign/eligibility/placement/public/preview/docs | P18 |
| P19-T08 | Public placement/homepage short-TTL cache + invalidation on create/update/activate/pause/delete (R7) | P19-T05 |

**AC P19:** auth + lifecycle + public filter ITs green; OpenAPI paths present; cache miss after Admin mutation without waiting for TTL.

### P20 — Storefront homepage

Wire API-driven hero/promos; FoodMart static fallback (R3); consume public homepage API (server implements R7).

### P21 — Admin UI

Full Admin UX + upload `purpose=PROMOTION` + activate/pause tests; allow ACTIVE strategy change with warning copy (R4).

### P22 — Checkout pricing, snapshots, expiry job

**Audit baseline (2026-10-31) — do not re-litigate without conflict:**

| Area | Finding |
|------|---------|
| Schema | `144-order-promotion-discount.yaml` already adds `orders.discount_amount`, `orders.promotion_summary`, `order_promotion_snapshots` (+ FKs `ON DELETE SET NULL`). **No new Liquibase required** for P22 core. Optional additive `145` only if expiry job needs a composite index. |
| Money | `NUMERIC(12,2)` / JPA `precision=12,scale=2`; ADR D2 rounding **HALF_UP** scale 2. Cart/order line math already uses `BigDecimal`. |
| Checkout | `CheckoutService.checkout` is `@Transactional`: cart lock → validate → **reserve** → build order/items → `total = subtotal + shipping` (**discount unused**) → mock pay → confirm/release → clear cart. Idempotent replay returns existing order. |
| DTOs | `CheckoutRequest` has `@JsonIgnoreProperties(ignoreUnknown=true)` and **no** `preferCampaignIds` (R2). `CheckoutResponse` / `OrderResponse` lack `discountAmount` / `appliedPromotions` (ADR D7 gap). FE `CheckoutResult` mirrors that. |
| Domain | `PromotionCampaign.isPricingEffective(Instant)` = ACTIVE ∧ inclusive UTC window. Strategy ACTIVE required for pricing. Order-level eligibility = OR when both sets set (P22); empty both = all. Stacking = ADR D2 + end-trim so snapshots sum to order discount. |
| Scheduling | `SchedulingConfig` + `InventoryReservationExpiryJob` pattern; inject `Clock` (`TimeConfig.utcClock`). |
| Inventory | Leave reserve / confirm / release / payment boundaries **unchanged**. |

**Clarifications (ADR-aligned / P22-approved):**

1. **Eligible base** = sum of line totals for cart lines matching campaign eligibility.
2. **Order-level `%` / fixed eligibility (P22 decision):** empty product **and** category sets → all lines; only one set populated → that set; **both** populated → line matches if product **OR** category matches. `PRODUCT_DISCOUNT` / `CATEGORY_DISCOUNT` remain product-only / category-only.
3. **`min_order_amount` / `min_quantity`** = evaluated against the **full cart** (subtotal / sum of quantities), not the eligible subset — ADR “against the cart”.
4. **Aggregate cap + end-trim (P22 decision):** compute each campaign discount per ADR D2; `order.discount_amount = min(Σ, subtotal)`; trim excess from the **end** of the applied list; persist only final applied amounts; drop promotions whose final amount is 0; snapshot amounts **sum exactly** to `order.discount_amount`.
5. **Clock** = inject `Clock` and use `clock.instant()` (UTC).
6. **FAILED payment orders** already persist today; attach discount + snapshots on that same persist path so payment amount matches intended total. Abort/`@Transactional` rollback still leaves **no** order/snapshot rows.
7. **Frontend**: extend types + show order-level discount on checkout success / order detail only as needed for ADR D7 contract. No cart “pick a campaign” UI. No Flutter.

**Task table**

| ID | Task | Depends | Notes |
|----|------|---------|-------|
| P22-T01 | `PromotionPricingService` (+ unit tests AC-S01…S08, caps, rounding, UTC bounds, empty/zero cart) | P18–P19 | Pure apply; load candidates via new repo query or campaign repo; **no** client IDs |
| P22-T02 | Wire into `CheckoutService`; set `discount_amount` / `promotion_summary`; persist `OrderPromotionSnapshot` rows; `total = subtotal + shipping − discount` (≥ 0); extend `CheckoutResponse` | P22-T01 | After line/subtotal build, **before** payment; keep inventory/payment flow |
| P22-T03 | Extend `OrderResponse` (+ mapper) with `discountAmount` + `appliedPromotions`; legacy orders ⇒ `0` / empty list | P22-T02 | Immutable snapshot read — never reprice from live campaigns |
| P22-T04 | Minimal FE: checkout/order types + display order-level discount from API | P22-T02/T03 | Contract consumption only |
| P22-T05 | `CampaignExpiryJob`: ACTIVE ∧ `now > ends_at` → EXPIRED; idempotent; UTC `Clock`; IT | P19 | Lifecycle hygiene only (R9); checkout must not depend on job |
| P22-T06 | Checkout / order / expiry integration matrix (AC list in TASKS); full relevant IT suite green | P22-T02…T05 | Fixed `Clock`; no `sleep` |

**AC P22 (release):** totals invariant; no preferCampaignIds; order-level discount only; reservation/payment unchanged; checkout ignores past-window ACTIVE without needing the job; snapshots survive campaign/strategy delete; no-promotion path ≡ legacy totals with `discount_amount = 0`.

### P23 — Hardening

Full matrix §13, demo seed, `make test-promotions`, docs, TASKS/PROGRESS.

---

## 15. Global release acceptance criteria

1. Homepage hero + two promo slots from Admin placements (not React constants for those slots); FoodMart static fallback when empty (R3).
2. Strategy / Campaign / Placement roles match §2 / ADR-020 D1.
3. Stacking matches ADR-020 D2; AC-S* pass; no client campaign preference (R2).
4. Snapshots preserve history after campaign edit/delete/strategy change; order-level discount only (R4/R5); AC-H* pass.
5. Migrations additive `140+`; existing orders `discount_amount = 0`; no-campaign checkout unchanged; AC-M* pass.
6. Shared upload `?purpose=` with isolated paths; AC-U* pass (R6).
7. Lifecycle/UTC/ACTIVE+future-start/ACTIVE strategy edit; expiry job present but non-authoritative; AC-L* pass (R1/R9).
8. Placement cache short TTL + mutation invalidation (R7).
9. `BANNER_ONLY` never changes totals.
10. CUSTOMER cannot mutate promotions.
11. Stock reservation/confirm behavior unchanged.
12. Admin Web + Storefront Web only (R8); no Postgres image bytes; no user `.env` / volume wipe required.

---

## 16. Finalized decisions R1–R9 (Accepted with ADR-020)

No unresolved R1–R9 approval remains. Normative detail: ADR-020 D6 / D8 / D9 / D10 / D11.

| ID | Accepted decision |
|----|-------------------|
| **R1** | Manual activation only. No automatic activation at campaign start. `ACTIVE` may have a future `starts_at`; effectiveness = status ∧ window (no DB flip at start). |
| **R2** | Omit `preferCampaignIds` from checkout. Backend determines applicable campaigns. |
| **R3** | Static FoodMart artwork as homepage fallback when no active placements are available. |
| **R4** | Allow changing strategy of an ACTIVE campaign. Preserve historical order snapshots. Revalidate updated configuration at runtime. |
| **R5** | Order-level discount only. No line-level discount persistence on `order_items`. |
| **R6** | Shared upload endpoint with explicit `purpose` parameter. Product and promotion paths isolated. |
| **R7** | Short-TTL cache for homepage placements **and** invalidate on create/update/activate/pause/delete (and related mutations). TTL is fallback only — do not rely on TTL alone. |
| **R8** | Defer Flutter parity. MVP = Admin Web + Storefront Web. |
| **R9** | Include expiry job in MVP for lifecycle state maintenance. Checkout/public APIs independently enforce time boundaries and must not depend on the job. |

### Cache invalidation triggers (R7) — required

Invalidate public placement/homepage cache on at least:

- placement create / update / delete
- campaign create / update / delete
- campaign activate / pause / schedule
- strategy activate/deactivate when dependents’ placement-effectiveness can change

### P19/P22 task notes

- P19: implement public placement cache + invalidation (R7).
- P22-T05: **required** expiry job (R9); add IT proving checkout ignores stale ACTIVE past `ends_at` without the job.

---

## 17. Out of scope

- Real payment gateways, tax engines, FX.
- BUY_X_GET_Y / FREE_SHIPPING implementation.
- Coupon codes / referral / first-order-only.
- Image bytes in PostgreSQL.
- Editing historical Liquibase `≤130`.
- Resetting Docker volumes / modifying user `.env`.
- Implementation in this planning refinement task.

---

## 18. Verification commands (when implementing)

```bash
cd backend && ./mvnw -Dtest=Promotion*,CheckoutIntegrationTest,OrderLifecycleIntegrationTest,ProductImage* test
cd frontend && npm test -- --run src/features/admin/promotions src/features/promotions src/pages/HomePage.test.tsx
make test-promotions   # added in P23
```

---

## Phase complete when

All P18–P23 tasks in `TASKS.md` are `DONE` with evidence; ADR-020 remains **Accepted** with R1–R9 locked; AC-E/S/H/M/U/L and global release criteria satisfied without regressing inventory/checkout idempotency.
