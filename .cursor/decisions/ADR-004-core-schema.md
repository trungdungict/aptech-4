# ADR-004: Core e-commerce schema conventions

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P03-T02, P04-*, P06-*

## Context

Need a PostgreSQL schema for identity, catalog, cart, addresses, orders, payments, and refresh tokens that supports JWT auth, inventory-safe checkout, and audit-friendly order history without mixing demo seed into migrations.

## Decision

- UUID primary keys with `gen_random_uuid()` defaults.
- Money as `NUMERIC(12,2)` plus `CHAR(3)` currency (default `VND`).
- Passwords: `users.password_hash` only; refresh tokens: `refresh_tokens.token_hash` (SHA-256 hex) only.
- One cart per user (`carts.user_id` UNIQUE); `cart_items` UNIQUE `(cart_id, product_id)`.
- Inventory in separate `inventory` table (1:1 with products).
- Order lines store `product_name`, `product_sku`, and `unit_price` snapshots; `product_id` nullable on delete (`SET NULL`).
- Shipping address snapshotted onto `orders` at checkout (source rows remain in `addresses`).
- Role seeds (`CUSTOMER`, `ADMIN`) are reference data in schema migration; catalog/demo users stay in P08 seed.

## Alternatives considered

1. Soft money as integer minor units only — deferred; NUMERIC is clearer for VND demo grading.
2. Embed stock on `products` — rejected for clearer locking/ADR path in P06.
3. Store raw refresh JWT — rejected (security).

## Consequences

- Positive: clear FKs/indexes; migration/seed separation; supports ownership checks.
- Negative: address data duplicated onto orders (intentional snapshot).
- Follow-ups: P03-T03 JPA entities; P06 inventory locking ADR; P08 demo seed.
