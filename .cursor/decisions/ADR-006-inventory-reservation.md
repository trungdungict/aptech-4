# ADR-006: Inventory reservation (soft hold before checkout)

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P06-T02, P06-T03, P06-T04

## Context

Cart (P06-T01) must not decrement stock. Checkout (P06-T03) needs a concurrent-safe hold so two customers cannot oversell the last units. ADR-004 already separated `inventory` from `products` and deferred locking to P06.

## Decision

### Stock model (`inventory`)

| Field | Meaning |
|-------|---------|
| `quantity_on_hand` | Physical units in warehouse |
| `quantity_reserved` | Units held by **ACTIVE** (non-expired) reservations |
| **available** | `quantity_on_hand - quantity_reserved` (computed; never stored) |

Invariants (DB CHECKs):

- `quantity_on_hand >= 0`
- `quantity_reserved >= 0`
- `quantity_reserved <= quantity_on_hand`

### Reservation aggregate

- Table `stock_reservations`: stable id, `user_id`, optional `idempotency_key` (unique per user), status, `expires_at`, timestamps.
- Table `stock_reservation_items`: `(reservation_id, product_id)` unique, positive quantity.
- Statuses: `ACTIVE` → `CONFIRMED` | `RELEASED` (terminal). Expired ACTIVE rows are released (lazy + scheduled cleanup).

### Operations

1. **reserve** — sort product ids; for each line run conditional `UPDATE … SET quantity_reserved = quantity_reserved + :q WHERE available >= :q`. Insert reservation + items in the same transaction. TTL from config (`aptech.inventory.reservation-ttl-seconds`, default **900**).
2. **release** — idempotent: if already `RELEASED`/`CONFIRMED`, no-op success; if `ACTIVE`, decrement reserved and set `RELEASED`.
3. **confirm** — for checkout payment success only (service API, not a public “pay” shortcut): decrement **both** `quantity_on_hand` and `quantity_reserved` by the reserved qty (sells held stock once). Rejects non-ACTIVE or expired.
4. **expire** — treat `expires_at <= now` as releasable; same stock effect as release.

### Concurrency

- Prefer **atomic conditional UPDATEs** on `inventory` (PostgreSQL row updates) over read-then-write in Java.
- Multi-line reserves lock/update products in **ascending product_id** order to reduce deadlocks; limited retry on deadlock.
- Idempotency key prevents double-hold on client retry.

### Non-goals (this ADR)

- Cart add/update does **not** reserve.
- Payment, order creation, refunds → P06-T03 / P06-T04.
- No hard delete of inventory history for demo.

## Alternatives considered

1. Decrement `quantity_on_hand` at reserve time — rejected; conflates hold with sale and complicates release/confirm.
2. Optimistic `@Version` only — acceptable adjunct but conditional UPDATE is clearer for “available >= qty”.
3. Redis-only holds — rejected for graduation demo consistency with Postgres source of truth.

## Consequences

- Positive: no oversell under concurrent reserve; clear lifecycle for checkout.
- Negative: requires TTL cleanup; reserved counter must stay consistent with ACTIVE rows.
- Follow-ups: P06-T03 calls `InventoryReservationService.confirm` after mock payment; optional admin stock adjust later.
