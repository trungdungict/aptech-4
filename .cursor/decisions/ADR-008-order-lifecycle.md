# ADR-008: Order lifecycle transitions

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P06-T04

## Context

Checkout (ADR-007) sets order to `PAID` or `FAILED` with mock payment and inventory confirm/release. Customers need to list/view/cancel own orders; admins need fulfillment status updates without rewriting money, ownership, or payment records.

## Decision

### Order vs payment

| Concern | Owner |
|---------|--------|
| `OrderStatus` | Fulfillment / lifecycle APIs |
| `PaymentStatus` | Checkout + payment gateway only |

Admin/customer order status endpoints **must not** mutate payment rows, totals, items, or `user_id`.

### Allowed transitions

```
PENDING_PAYMENT ──customer/admin cancel──► CANCELLED
PAID ──admin──► PROCESSING | CANCELLED
PROCESSING ──admin──► SHIPPED | CANCELLED
SHIPPED ──admin──► COMPLETED

Terminal (no outbound): COMPLETED, CANCELLED, FAILED
```

- `PAID` / `FAILED` are written by checkout only (not via status PATCH).
- Customer cancel: **`PENDING_PAYMENT` → `CANCELLED` only** (edge case; successful checkout leaves `PAID`).
- Admin cancel after `PAID`/`PROCESSING`: **status-only**. No automatic refund, no stock restock (inventory already confirmed at payment). Restock/refund is a future task if required.

### Concurrency

Status updates load the order with `PESSIMISTIC_WRITE`, re-check current status, then apply transition.

### APIs

- Customer: `GET /api/v1/orders`, `GET /api/v1/orders/{id}`, `POST /api/v1/orders/{id}/cancel`
- Admin: `GET /api/v1/admin/orders`, `GET /api/v1/admin/orders/{id}`, `PATCH /api/v1/admin/orders/{id}/status`

History responses use `OrderItem` snapshots (name/price), never live catalog prices.

## Alternatives considered

1. Allow customer cancel of `PAID` with restock — rejected without refund workflow.
2. Let admin set any status freely — rejected (breaks payment invariants).

## Consequences

- Positive: clear fulfillment path; checkout remains source of `PAID`/`FAILED`.
- Negative: cancelled-after-paid does not restore inventory in this phase.
- Follow-ups: optional restock/refund ADR later; P07+ tests.
