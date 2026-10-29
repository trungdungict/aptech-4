# ADR-015: Redis cache for public catalog reads

- **Status:** Accepted
- **Date:** 2026-10-29
- **Related tasks:** P13-T01, P13-T02

## Context

Public category/product discovery is read-heavy. Redis is already provisioned (Compose + Spring Data Redis). Admin mutations and inventory soft-holds can change what the public catalog returns (`active`, price, category, `inStock`).

## Decision

1. Cache **only** public reads via Spring Cache + Redis:
   - `GET` active categories list / detail
   - `GET` public product list (all discovery params) / detail
2. Do **not** cache admin lists, cart, checkout, orders, or auth/profile.
3. **TTL:** categories 120s, products 60s (configurable under `aptech.cache.catalog.*`). Short product TTL limits stale `inStock` windows.
4. **Keys:** deterministic composite for product lists (`q|categoryId|minPrice|maxPrice|inStock|page|size|sort`); category list uses fixed `all`; details use UUID.
5. **Invalidation trade-off:** product-list and product-detail caches are cleared with `allEntries` on product/category admin writes and on inventory reserve/confirm/release (combinatorial list keys make precise eviction impractical). Category list/detail cleared on category writes; product caches also cleared when categories change (embedded `categoryName` / public filters).
6. **Failure:** `CacheErrorHandler` logs Redis get/put/evict failures and continues — PostgreSQL remains source of truth (no hard fail of public catalog on cache errors). Redis remains required at startup (existing app posture).

## Alternatives considered

1. Cache HTTP responses in a filter — rejected (duplicates service DTOs; harder auth/error handling).
2. Per-key product-list eviction — rejected (too many param combinations).
3. Ignore inventory for cache invalidation — rejected (`inStock` filter would lie until TTL).

## Consequences

- Positive: fewer DB hits for repeated public discovery; mutations drop stale public views immediately.
- Negative: admin/inventory writes flush broad product cache regions (acceptable at demo scale).
- Follow-ups: optional metrics for hit ratio; production keystore/signing unrelated.
