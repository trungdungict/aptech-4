# OpenAPI / Swagger UI

Baseline API documentation uses **springdoc-openapi 3.2.0** (Spring Boot **4.1.2** compatible).

## Local URLs

| Resource | URL |
|----------|-----|
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| OpenAPI YAML | `http://localhost:8080/v3/api-docs.yaml` |
| Swagger UI | `http://localhost:8080/swagger-ui` (redirects to the UI index) |

Only `/api/v1/**` controllers are scanned into the document (`springdoc.paths-to-match`). Actuator is **not** included (`show-actuator: false`).

## JWT in Swagger UI

1. Open Swagger UI.
2. Click **Authorize**.
3. Enter a JWT **access** token (no `Bearer ` prefix required in the dialog).
4. Call protected operations.

The document declares HTTP Bearer scheme `bearer-jwt`. Public auth operations (`/api/v1/auth/register|login|refresh|logout`) and `GET /api/v1/health` clear the security requirement.

Refresh/logout take the refresh JWT in the **JSON body** (`refreshToken`), never as a Bearer header.

Admin operations live under `/api/v1/admin/**` and require an access token with `ROLE_ADMIN` (e.g. `/api/v1/admin/categories`). Public catalog reads such as `GET /api/v1/categories` and `GET /api/v1/products` clear the bearer requirement. Customer private resources need ownership checks in addition to authentication (see `SECURITY.md`).

### Categories (P05-T01)

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/categories` | Public (active only) |
| GET | `/api/v1/categories/{id}` | Public (active only) |
| GET | `/api/v1/admin/categories` | ADMIN (all statuses) |
| POST | `/api/v1/admin/categories` | ADMIN |
| PUT | `/api/v1/admin/categories/{id}` | ADMIN |
| PATCH | `/api/v1/admin/categories/{id}/status` | ADMIN (soft activate/deactivate; no hard delete) |

### Products (P05-T02) — public discovery

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/products` | Public (active products in active categories) |
| GET | `/api/v1/products/{id}` | Public (same visibility rules) |

**Query parameters (`GET /api/v1/products`):**

| Param | Semantics |
|-------|-----------|
| `page` | **Zero-based** page index (default `0`) |
| `size` | Page size (default `20`, max `100`; larger → `400`) |
| `sort` | `field,asc\|desc` — whitelist: `name`, `price` (maps to `unitPrice`), `createdAt`; always ties on `id` |
| `q` | Trimmed keyword; case-insensitive match on name **or** description; blank ignored |
| `categoryId` | Must be an **active** category; missing/inactive → `404` |
| `minPrice` / `maxPrice` | Inclusive `BigDecimal`; must be `>= 0`; `minPrice > maxPrice` → `400` |
| `inStock` | `true` → inventory `quantity_on_hand > 0`; `false` → no inventory row or quantity `0` |

**Page response shape:** `{ items, page, size, totalElements, totalPages, first, last }`.

`imageUrl` is always `null` until a media column exists (not invented). Response includes `active` (public rows are always `true`).

### Products (P05-T03) — admin

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/admin/products` | ADMIN (all statuses; paginated) |
| GET | `/api/v1/admin/products/{id}` | ADMIN |
| POST | `/api/v1/admin/products` | ADMIN |
| PUT | `/api/v1/admin/products/{id}` | ADMIN |
| PATCH | `/api/v1/admin/products/{id}/status` | ADMIN (soft activate/deactivate) |
| GET | `/api/v1/admin/products/{id}/inventory` | ADMIN |
| PUT | `/api/v1/admin/products/{id}/inventory` | ADMIN (set absolute `quantityOnHand`) |

**Admin rules:**

- Create/update require an **active** category (`400` if missing/inactive).
- SKU unique (case-insensitive app check; DB unique on `sku`) → `409` on conflict.
- Request bodies ignore unknown fields (e.g. `quantityOnHand` / `inventory`) — **no inventory write** on product create/update (use inventory endpoints).
- New products start `active=true`; deactivate hides them from public product APIs.
- **No DELETE** endpoint (soft-disable only) → `405` if attempted.
- Admin list query: `q`, `categoryId` (any existing category), `active`, same page/size/sort as public.

**Admin inventory (ADR-006):**

- One `inventory` row per product (`product_id` PK). `available = quantity_on_hand − quantity_reserved`.
- `GET …/inventory` returns `{ productId, quantityOnHand, quantityReserved, available, updatedAt }` (zeros if row missing).
- `PUT …/inventory` body `{ "quantityOnHand": <int >= 0 }` sets absolute on-hand; creates the row when missing; rejects when `quantityOnHand < quantityReserved` (`400`); uses a pessimistic lock; does **not** change reservation/confirm paths.
- CUSTOMER / anonymous → `401`/`403`. Checkout still reserves via `/api/v1/inventory/reservations`.

### Cart (P06-T01) — customer

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/cart` | CUSTOMER (JWT owner) |
| POST | `/api/v1/cart/items` | CUSTOMER |
| PATCH | `/api/v1/cart/items/{itemId}` | CUSTOMER |
| DELETE | `/api/v1/cart/items/{itemId}` | CUSTOMER |
| DELETE | `/api/v1/cart/items` | CUSTOMER (clear all) |

**Rules:**

- Owner is always `sub` from the access JWT — never body/query `userId`.
- ADMIN → **403** (customer-only); other user's itemId → **404** (no leak).
- Add requires purchasable product (active + active category). Duplicate product **merges** quantity (unique `cart_id,product_id`).
- Quantity `1..99`. Server prices from `Product.unitPrice` (`BigDecimal`); client price fields ignored.
- Inactive products already in cart: shown with `available=false` / `hasUnavailableItems=true`; quantity updates rejected; remove allowed.
- **No inventory reservation or stock mutation** (P06-T02 / checkout).

### Inventory reservations (P06-T02)

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/inventory/reservations` | CUSTOMER |
| GET | `/api/v1/inventory/reservations/{id}` | CUSTOMER (owner) |
| POST | `/api/v1/inventory/reservations/{id}/release` | CUSTOMER (owner; idempotent) |

**Lifecycle (ADR-006):** `ACTIVE` → `RELEASED` | `CONFIRMED`. TTL default **900s** (`INVENTORY_RESERVATION_TTL_SECONDS`).  
`available = quantity_on_hand - quantity_reserved`. Insufficient stock → **409**. Idempotency: unique `(user_id, idempotencyKey)`.  
**Confirm** is **service-only** (`InventoryReservationService.confirm`) for P06-T03 checkout — not exposed as a payment-bypass HTTP endpoint. Cart never reserves.

### Addresses (minimal; P09-T04 storefront)

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/addresses` | CUSTOMER (JWT owner) |
| POST | `/api/v1/addresses` | CUSTOMER |

Schema existed since P03; REST added so checkout can supply a real owned `addressId`. List is owner-scoped; create validates recipient/line1/city.

### Checkout (P06-T03) — mock payment

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/checkout` | CUSTOMER |

**Body:** `idempotencyKey`, `addressId` (must belong to caller). Optional `simulateFailure` only when `MOCK_PAYMENT_ALLOW_CLIENT_FORCE_FAILURE=true`.

**Flow (ADR-007):** lock cart → reserve stock (`checkout:{key}`) → create order + payment → mock charge → success: confirm + `PAID` + clear cart; failure: release + `FAILED` + keep cart.

**Idempotency:** unique `(user, key)` + request hash (address + cart lines + simulateFailure). Same key/hash → same result; different payload → **409**.

**Mock payment:** provider `MOCK` only — not a real gateway. Clients cannot set `paymentStatus=SUCCESS`.

### Orders (P06-T04) — lifecycle

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/orders` | CUSTOMER (own orders; paginated) |
| GET | `/api/v1/orders/{orderId}` | CUSTOMER (owner) |
| POST | `/api/v1/orders/{orderId}/cancel` | CUSTOMER (`PENDING_PAYMENT` → `CANCELLED` only) |
| GET | `/api/v1/admin/orders` | ADMIN (optional `status` filter) |
| GET | `/api/v1/admin/orders/{orderId}` | ADMIN |
| PATCH | `/api/v1/admin/orders/{orderId}/status` | ADMIN (whitelist transitions; ADR-008) |

**Transitions:** `PAID→PROCESSING|CANCELLED`, `PROCESSING→SHIPPED|CANCELLED`, `SHIPPED→COMPLETED`. Terminal: `COMPLETED`, `CANCELLED`, `FAILED`.  
Status PATCH does **not** change payment, totals, items, or owner. Cancel after `PAID` is status-only (no refund/restock). History uses order-item snapshots.

### Notifications (order lifecycle)

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/notifications` | Authenticated (recipient-scoped; paginated) |
| GET | `/api/v1/notifications/unread-count` | Authenticated |
| PATCH | `/api/v1/notifications/{id}/read` | Authenticated (owner only → else `404`) |
| PATCH | `/api/v1/notifications/read-all` | Authenticated |

**Semantics (ADR-017):** rows are created only after successful order events in the same DB transaction. There is **no** cross-user aggregate feed — ADMIN operational alerts are personal rows for every enabled user with `RoleCode.ADMIN`.

| Event | Customer (order owner) | Enabled ADMIN users |
|-------|------------------------|---------------------|
| Checkout → `PAID` | `ORDER_PAID` | `ORDER_PAID` |
| Customer/admin cancel → `CANCELLED` | `ORDER_CANCELLED` | `ORDER_CANCELLED` |
| Admin status → `PROCESSING`/`SHIPPED`/`COMPLETED` | `ORDER_STATUS_CHANGED` | — |

Failed checkout (`FAILED`), rejected transitions, idempotent replays, and unchanged status do **not** create new rows (unique `(recipient_user_id, dedupe_key)`).

### Notifications — WebSocket push (ADR-018)

| Item | Value |
|------|--------|
| Endpoint | `WS /ws` (STOMP; same-origin via nginx `http://localhost:8088/ws`) |
| CONNECT auth | STOMP header `Authorization: Bearer <access JWT>` (not query string) |
| Subscribe | `/user/queue/notifications` only |
| Payload | Same shape as `NotificationResponse` |
| Timing | After DB commit of the notification row |

REST endpoints above remain the source of truth; clients refetch after reconnect.

## Disabling docs (production-like)

Prefer profile `prod`:

```bash
SPRING_PROFILES_ACTIVE=prod
```

Or environment flags (override defaults in `application.yml`):

```bash
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

Do **not** leave Swagger UI enabled on a public EC2 HTTP demo unless explicitly required for grading.
