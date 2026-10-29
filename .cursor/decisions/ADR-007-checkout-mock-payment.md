# ADR-007: Checkout orchestration + mock payment

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P06-T03, P06-T04

## Context

P06-T02 provides soft-hold inventory (`reserve` / `release` / service-only `confirm`). Checkout must create durable orders with price snapshots, charge a **mock** payment (no real gateway), and keep stock/cart consistent on success and failure. Retries must not double-charge or double-create orders.

## Decision

### Endpoint

`POST /api/v1/checkout` (CUSTOMER JWT). Body: `idempotencyKey`, `addressId`. Owner from JWT `sub` only. Shipping snapshot copied from the caller's address row.

### Idempotency

Table `checkout_idempotency` unique `(user_id, idempotency_key)` stores `request_hash` (SHA-256 of canonical payload) + `order_id`.

- Same key + same hash → return existing checkout result.
- Same key + different hash → **409**.
- Race on insert → loser reloads winner row.

### Transaction sequence (single DB transaction; mock is in-process)

1. Load/lock cart; reject empty / unavailable lines.
2. Load address owned by user; reject otherwise (**404**).
3. Price lines from `Product` (`BigDecimal` scale 2, `HALF_UP`); shipping **0** for demo.
4. `InventoryReservationService.reserve` with key `checkout:{idempotencyKey}`.
5. Persist `ShopOrder` (`PENDING_PAYMENT`) + `OrderItem` snapshots + `Payment` (`PENDING`, provider `MOCK`).
6. `MockPaymentGateway.charge` (deterministic; never trust client `paymentStatus`).
7. **Success:** payment `SUCCEEDED`, `confirm` reservation, order `PAID`, **clear cart**, save idempotency.
8. **Failure:** payment `FAILED`, `release` reservation, order `FAILED`, **keep cart**, save idempotency.

### Mock payment

- Interface `PaymentGateway` + `MockPaymentGateway`.
- Default outcome `SUCCEEDED` (`aptech.payment.mock.default-outcome`).
- Optional `simulateFailure` on request honored only when `aptech.payment.mock.allow-client-force-failure=true` (tests/demo). Clients cannot set `paymentStatus=SUCCESS`.

### Non-goals

- Real card data / PSP integration.
- Full order history APIs (P06-T04).
- Non-zero shipping rules.

## Alternatives considered

1. Two-phase (commit order before mock call) — unnecessary for in-process mock; adds compensation complexity.
2. Clear cart before payment — rejected; failure would lose cart.

## Consequences

- Positive: atomic demo checkout; ADR-006 confirm path used; idempotent retries.
- Negative: FAILED orders retained for audit/idempotency replay.
- Follow-ups: P06-T04 customer/admin order APIs.
