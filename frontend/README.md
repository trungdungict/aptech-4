# Aptech-4 Frontend

React + TypeScript customer storefront and admin shell built with Vite.

Current scope: auth, catalog, cart/checkout/orders, and **admin shell + role gate** (CRUD modules come later).

## Prerequisites

- Node.js `^20.19.0` or `>=22.12.0` (Vite 8 engines)
- npm 10+ (lockfile is npm; do not add yarn/pnpm lockfiles)
- Backend API available for manual smoke (Compose Postgres/Redis + Spring Boot on `:8080`)

## Setup

```bash
cd frontend
cp -n .env.example .env.local   # optional local overrides
npm install
```

Root monorepo `.env.example` also documents `VITE_API_BASE_URL` and `CORS_ALLOWED_ORIGINS` (must include `http://localhost:5173`).

## Scripts

| Command | Purpose |
|---------|---------|
| `npm run dev` | Vite dev server (`http://localhost:5173`) |
| `npm run build` | Typecheck (`tsc -b`) + production bundle |
| `npm run preview` | Preview the production build |
| `npm run typecheck` | Strict TypeScript project references check |
| `npm test` | Vitest unit/UI tests with mocked `fetch` (jsdom) |
| `npm run test:watch` | Vitest watch mode |
| `npm run lint` | Oxlint |

## Environment

| Variable | Exposed in browser | Purpose |
|----------|--------------------|---------|
| `VITE_API_BASE_URL` | Yes | REST API prefix (default `http://localhost:8080/api/v1`) |

Accepted forms: absolute `http(s)://…/api/v1` (Vite → host Spring) or path-absolute `/api/v1` (Compose/EC2 same-origin nginx).

| Context | Recommended value |
|---------|-------------------|
| `npm run dev` | `http://localhost:8080/api/v1` |
| Compose `web` image | `/api/v1` (build arg `VITE_STACK_API_BASE_URL`; nginx proxies to `api:8080`) |
| Future public host | `/api/v1` behind TLS reverse proxy, or `https://<host>/api/v1` |

Rules:

- Only `VITE_*` values may be embedded in the client bundle.
- Never put credentials, private keys, JWT secrets, or database passwords in frontend env files.
- Do not hardcode production hosts; configure via env at **build**/dev time (ADR-002, ADR-016). Vite cannot change a value already compiled into `dist/`.
- Local stack: `make deploy-local` → `http://localhost:8088` (see root README).

## Auth routes and API contract

| UI route | Backend |
|----------|---------|
| `/login` | `POST /api/v1/auth/login` |
| `/register` | `POST /api/v1/auth/register` (201 + session; role always `CUSTOMER`) |
| (session restore / 401 retry) | `POST /api/v1/auth/refresh` |
| Sign out | `POST /api/v1/auth/logout` → **204** |

Optional `?next=/path` on login is open-redirect safe (relative paths only). Cart/checkout/orders require authentication (`RequireAuth`).

### Token lifecycle (ADR-013)

- Access token: **memory only**; sent as `Authorization: Bearer …`.
- Refresh token: **`sessionStorage`** via isolated `token-store` (not `localStorage`). Required because the API returns refresh tokens in JSON, not HttpOnly cookies (ADR-005).
- Refresh is single-flight; failure clears local session.
- Logout always clears local state; private TanStack Query caches for cart/orders/addresses are removed on logout / user switch (`AuthQueryBridge`). Tokens are never placed in query keys.
- Passwords and tokens are never written to logs or generic error messages.

## Catalog routes and filters

| UI route | Backend |
|----------|---------|
| `/products` | `GET /api/v1/products` + `GET /api/v1/categories` |
| `/products/:productId` | `GET /api/v1/products/{id}` (+ authenticated `POST /api/v1/cart/items`) |

Supported list query params (must match backend): `q`, `categoryId`, `minPrice`, `maxPrice`, `inStock`, `page` (0-based), `size` (default 20, max 100), `sort` (`name|price|createdAt`,`asc|desc`; default `name,asc`).

## Cart, checkout, and orders

| UI route | Backend |
|----------|---------|
| `/cart` | `GET/POST/PATCH/DELETE /api/v1/cart…` |
| `/checkout` | `GET/POST /api/v1/addresses` + `POST /api/v1/checkout` |
| `/orders` | `GET /api/v1/orders` |
| `/orders/:orderId` | `GET /api/v1/orders/{id}` (+ `POST …/cancel` when `PENDING_PAYMENT`) |

### Checkout / idempotency

- Body fields: `idempotencyKey` (string, required), `addressId` (UUID, required). Optional `simulateFailure` is only honored when the backend mock force-failure flag is enabled — the UI does not send it in the normal flow.
- The SPA generates one idempotency key per logical attempt (`crypto.randomUUID`) and **reuses** it for retries of that attempt. A new key is issued only when the user starts a new attempt.
- Network/unknown outcomes do **not** auto-retry and do **not** claim success. Users can open Orders or intentionally “Retry same attempt”.
- Payment is **mock/demo only** (ADR-007). A `201` response may still report `orderStatus=FAILED` / payment failed; the UI shows the server status. Cart is cleared only when the backend reports `cartCleared` / paid success.
- Address REST (`GET`/`POST /api/v1/addresses`) was added as a minimal customer API so checkout can supply a real `addressId` (addresses table already existed).

### Known limitations

- No guest cart persistence or cart merge.
- No real payment gateway.
- Catalog `imageUrl` is currently always `null` from the API — UI shows a placeholder.

## Admin routes and role gate

| UI route | Backend |
|----------|---------|
| `/admin` | Shell + overview (`RequireAdmin`) |
| `/admin/products` | `GET /api/v1/admin/products` (+ status PATCH) |
| `/admin/products/new` | `POST /api/v1/admin/products` |
| `/admin/products/:id/edit` | `GET` + `PUT /api/v1/admin/products/{id}` |
| `/admin/categories` | `GET /api/v1/admin/categories` (+ status PATCH) |
| `/admin/categories/new` | `POST /api/v1/admin/categories` |
| `/admin/categories/:id/edit` | `PUT /api/v1/admin/categories/{id}` (loaded from list; no GET-by-id) |
| `/admin/orders` | `GET /api/v1/admin/orders` (`status`, `page`, `size`, `sort`) |
| `/admin/orders/:orderId` | `GET` + `PATCH /api/v1/admin/orders/{id}/status` |

**Role source:** `AuthResponse.user.roles` from trusted login / register / refresh responses (`CUSTOMER` | `ADMIN` matching backend `RoleCode`). The SPA does **not** decode JWTs, invent roles from email/URL/localStorage, or allow self-selected roles on register.

**Gating behavior (UX only):**

- Unauthenticated → redirect to `/login?next=…` (open-redirect safe via `safeInternalPath`)
- Authenticated without `ADMIN` (including empty/unknown roles) → access-denied page; admin shell is not mounted (fail closed)
- Authenticated with `ADMIN` → admin layout + catalog/order admin pages
- Session boot/refresh → loading state; no admin content flash

Backend `/api/v1/admin/**` remains the security boundary. Frontend tests do not prove backend authorization.

### Catalog admin semantics

- Product write body: `sku`, `name`, `description`, `unitPrice`, `currency`, `categoryId` only — **no inventory fields**.
- Category write body: `name`, `description`, `parentId` — slug is server-generated.
- Status changes use `PATCH …/status` with `{ active }` (soft-disable). There is **no hard delete**.
- Duplicate SKU / category name → **409**; inactive category on product write → **400**.

### Order admin semantics

- List/detail reuse `OrderResponse` (includes `userId`, not email). Item prices are checkout snapshots.
- Filters: optional `status`; sort whitelist `placedAt|totalAmount|status`,`asc|desc` (default `placedAt,desc`).
- Status updates: `PATCH …/status` with `{ status }` following ADR-008 whitelist only. Cannot set `PAID`/`FAILED` (checkout-owned). Invalid transition → **409**.
- No refunds, payment confirmation, or bulk operations in the UI.

## Local development against the API

```bash
# terminal 1 — deps + API (from repo root)
make up-deps
cd backend && ./mvnw spring-boot:run

# terminal 2 — SPA
cd frontend && npm run dev
```

Ensure `CORS_ALLOWED_ORIGINS` includes the Vite origin. No Vite proxy is required when `VITE_API_BASE_URL` points at the API.

## Directory layout

```
src/
  app/                 # providers, router, error boundary
  components/          # shared layout
  features/auth/       # api, pages, schemas, session, context, role helpers
  features/admin/      # shell, overview, products/categories/orders
  features/catalog/    # public product browse/detail
  features/cart/       # cart API + page
  features/checkout/   # addresses + checkout
  features/orders/     # history + detail
  lib/api/             # shared fetch client + ApiClientError
  pages/               # landing
  test/                # Vitest helpers
```

Path alias: `@/` → `src/`.

## Related docs

- ADR-002 — environment configuration
- ADR-005 — JWT Bearer security
- ADR-006 / ADR-007 / ADR-008 — reservation, checkout, orders
- ADR-012 — frontend Vite React scaffold
- ADR-013 — SPA token storage trade-offs
- `.cursor/plans/04-frontend-customer-admin.md`
