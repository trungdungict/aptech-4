# Security baseline

See ADR-005 for algorithm and claim decisions.

## Public routes

- `GET /api/v1/health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/categories` and `GET /api/v1/categories/{id}` (active categories only)
- `GET /api/v1/products` and `GET /api/v1/products/{id}` (active products in active categories only)
- Actuator `health` / `info`
- OpenAPI `/v3/api-docs/**` and Swagger UI `/swagger-ui/**` (disable in `prod`)
- `/error` (so missing authenticated routes return 404 instead of masking as 401)

Refresh JWTs must **not** be sent as Bearer access tokens; they belong in the refresh/logout JSON body only.

## RBAC matrix (P04-T04)

Authorities come from the **server-signed access JWT** `roles` claim (and login/register load roles from DB). Clients cannot supply roles via body, query, or custom headers.

| Surface | Rule | Notes |
|---------|------|-------|
| Public allow-list | No auth | Health, auth, OpenAPI, `/error` |
| `/api/v1/admin/**` | `ROLE_ADMIN` | Filter-chain matcher; includes categories, products, and `…/products/{id}/inventory` |
| `GET /api/v1/categories/**` | Public | Active categories only; inactive → omitted / 404 |
| `GET /api/v1/products/**` | Public | Active products + active category; inactive → omitted / 404 |
| Other `/api/v1/**` | Authenticated access JWT | Deny-by-default |
| Customer-only methods | `@PreAuthorize("hasRole('CUSTOMER')")` | ADMIN does **not** auto-inherit |
| Admin-only methods | `@PreAuthorize("hasRole('ADMIN')")` | Prefer under `/api/v1/admin/**` |
| `/api/v1/cart/**` | `ROLE_CUSTOMER` + JWT `sub` ownership | ADMIN **403**; foreign itemId → **404** |
| `/api/v1/inventory/reservations/**` | `ROLE_CUSTOMER` + JWT `sub` ownership | ADMIN **403**; foreign reservation → **404**; confirm is service-only |
| `POST /api/v1/checkout` | `ROLE_CUSTOMER` + JWT `sub` | Address must be owned; ADMIN **403**; mock payment never trusts client status |
| `/api/v1/orders/**` | `ROLE_CUSTOMER` + JWT `sub` ownership | Foreign orderId → **404**; ADMIN does not use these routes |
| `/api/v1/admin/orders/**` | `ROLE_ADMIN` | Filter-chain; status PATCH cannot forge payment/owner/totals |
| `/api/v1/notifications/**` | Authenticated + `recipient_user_id = JWT sub` | No cross-user read/mark; ADMIN ops alerts are personal rows for `RoleCode.ADMIN` users |
| `/ws` STOMP | Handshake permit-all; **CONNECT** requires access JWT | Subscribe limited to `/user/queue/notifications`; principal = JWT `sub`; no JWT in URL (ADR-018) |
| Private customer data | Auth **plus ownership** (`sub` / user id) | ADMIN role does **not** bypass ownership; use dedicated admin APIs |

### HTTP status

| Case | Status |
|------|--------|
| Missing / invalid / wrong-`token_use` Bearer | **401** Unauthorized |
| Valid token, insufficient role or ownership | **403** Forbidden |

Spring authorities use a single `ROLE_` prefix (`ROLE_CUSTOMER`, `ROLE_ADMIN`) via `SecurityRoles` (no `ROLE_ROLE_*`).

Register always assigns Liquibase-seeded `CUSTOMER`. There is **no** public API to create `ADMIN` accounts; demo admins belong in a later seed task.

## Auth responses (P04-T02 / P04-T03)

Register (`201`), login (`200`), and refresh (`200`) return:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "<uuid>",
    "email": "user@example.com",
    "fullName": "Display Name",
    "roles": ["CUSTOMER"]
  }
}
```

- Passwords are stored as BCrypt hashes only.
- Email is normalized to lowercase before persist/lookup.
- Login failures use a generic `Invalid email or password` message.
- Refresh failures use a generic `Invalid refresh token` message (no existence leak).

## Refresh lifecycle (P04-T03)

| Topic | Behavior |
|-------|----------|
| Storage | Only **SHA-256 hex** of the raw refresh JWT in `refresh_tokens.token_hash` (never plaintext) |
| Access TTL | `JWT_ACCESS_TTL_SECONDS` (default 900) — `expiresIn` in responses |
| Refresh TTL | `JWT_REFRESH_TTL_SECONDS` (default 604800) |
| Rotation | Each successful `/refresh` **revokes** the presented token (`revoked_at`) and issues a new one (`replaced_by_id`) |
| Client duty | **Replace** the stored refresh token with the newly returned value after every successful refresh |
| Concurrency | Row is locked (`PESSIMISTIC_WRITE`) during rotate; only one concurrent refresh of the same token succeeds |
| Reuse detection | Presenting a **already-revoked** refresh token revokes **all** active refresh tokens for that user, then returns 401 |
| Logout | `POST /logout` with `{ "refreshToken": "..." }` revokes **that session only** (`204`). Idempotent for unknown/already-revoked tokens |
| Access JWT after logout | **Not** invalidated immediately — tokens remain valid until `exp` (no denylist / versioning yet) |
| Disabled user | Refresh is rejected |

## Environment

| Variable | Purpose |
|----------|---------|
| `JWT_ACCESS_SECRET` | HS256 key for access tokens (≥32 chars, required) |
| `JWT_REFRESH_SECRET` | HS256 key for refresh tokens (≥32 chars, required, ≠ access) |
| `JWT_ISSUER` | Expected `iss` claim (default `aptech4-api`) |
| `JWT_ACCESS_TTL_SECONDS` | Access TTL (default 900) |
| `JWT_REFRESH_TTL_SECONDS` | Refresh TTL (default 604800) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated origins (no `*` with credentials) |

Missing/short/identical secrets fail application startup.

## Logging

Do not log passwords, raw JWTs, Authorization headers, or refresh-token hashes.

## Security regression suite

See [TESTING.md](TESTING.md) § Auth security suite (P07-T02) for the mapping of JWT / RBAC / refresh / CORS / hygiene tests.

## Demo seed

Demo admin/customer accounts are created only via explicit `make seed-demo` (ADR-010). Scoped demo reset requires `make reset-demo CONFIRM=yes` (ADR-011) and never wipes volumes. Production profile refuses seed/reset. Passwords are BCrypt-hashed; see [docs/DEMO_SEED.md](../../docs/DEMO_SEED.md).
