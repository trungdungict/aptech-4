# P09–P10 — React customer storefront and admin

## Objective

TypeScript React apps (or monorepo app with route segments) for customers and admins against the shared API.

## Prerequisites

- Backend API contracts for auth, catalog, cart, orders (OpenAPI or documented routes)
- `VITE_API_BASE_URL` in `.env.example`

## P09 — Customer storefront

- Auth pages, product browse/search, product detail, cart, checkout (mock pay), order history
- TanStack Query + RHF/Zod; Tailwind + shadcn/ui

## P10 — Admin dashboard

- Admin login (role gate), product/category CRUD, order list/status updates
- Shared API client utilities where practical

## Acceptance criteria

- Strict TS; Vitest + RTL for critical flows
- Env-based API URL; production build succeeds
- Admin routes reject non-admin users

## Verification

```bash
cd frontend && npm test && npm run lint && npm run build
```

## Risks

- Node 26 very new — pin Vite-compatible toolchain; document if engines need Node 22 LTS via nvm

## Phase complete when

P09–P10 tasks `DONE` with test/build evidence.
