# ADR-017: Persistent in-app order notifications

- **Status:** Accepted
- **Date:** 2026-10-29
- **Related:** ADR-008 (order lifecycle), ADR-007 (checkout)

## Context

Customers and admins need durable in-app alerts when meaningful order lifecycle events succeed. The stack is a modular monolith (ADR-001) with PostgreSQL — no Kafka, WebSockets, or microservices for MVP.

## Decision

### Persistence

- Table `notifications`: `id`, `recipient_user_id`, `type`, `title`, `message`, `order_id` (nullable, `ON DELETE SET NULL`), `dedupe_key`, `created_at`, `read_at`.
- Unique `(recipient_user_id, dedupe_key)` prevents duplicates on checkout idempotency replays and unchanged status.
- Recipient delete cascades notifications.

### Creation hooks (authoritative backend only)

| Successful event | Recipients | Type / dedupe pattern |
|------------------|------------|------------------------|
| Checkout charge → `PAID` | Order owner + all **enabled** users with `RoleCode.ADMIN` | `ORDER_PAID` / `ORDER_PAID:{orderId}:customer` and `…:admin:{adminId}` |
| Customer cancel or admin → `CANCELLED` | Owner + enabled ADMINs | `ORDER_CANCELLED` / same shape |
| Admin → `PROCESSING`/`SHIPPED`/`COMPLETED` | Owner only | `ORDER_STATUS_CHANGED` / `ORDER_STATUS:{orderId}:{status}` |

Hooks run inside the existing `@Transactional` order/checkout methods so order mutation and notification rows commit or roll back together.

**Not notified:** failed payment (`FAILED`), rejected transitions, cache reads, fake shipping/payment events the app cannot produce.

### API & authorization

- Recipient-scoped endpoints under `/api/v1/notifications` (`isAuthenticated()`).
- Ownership enforced by `recipient_user_id = JWT sub` — other users' ids return `404`.
- No separate admin aggregate inbox; ADMIN visibility is “each admin user receives their own operational copy.”

### Frontend MVP

- Bell + unread badge in customer and admin layouts; TanStack Query keys scoped by user id and cleared on logout/switch.
- Live updates: **ADR-018** STOMP WebSocket (primary). Optional slow REST fallback only while disconnected.

## Alternatives considered

1. WebSocket/SSE push in the first MVP — deferred to ADR-018 once persistence/REST were verified.
2. Single shared “admin feed” table without per-user rows — rejected (harder mark-as-read isolation).
3. Frontend-only mutation callbacks to invent notifications — rejected (not authoritative; duplicates/misses).

## Consequences

- Positive: durable, ownership-safe, atomic with order changes; reuses `RoleCode.ADMIN`.
- Negative: many ADMIN users ⇒ fan-out writes per event.
- Follow-ups: restock/refund notifications if those workflows appear.
