# ADR-012: Frontend Vite + React TypeScript scaffold

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P09-T01
- **Depends on:** ADR-002

## Context

Need a single React app foundation for the customer storefront (and later admin routes) that matches the playbook stack without blocking on full feature work.

## Decision

1. Host the web app under `frontend/` with **Vite + React + TypeScript (strict)**.
2. Use **Tailwind CSS v4** via `@tailwindcss/vite` and a **shadcn/ui-compatible** layout (`components.json`, CSS variables, `cn()` helper). Components are added later via shadcn CLI — do not replace this baseline.
3. Wire **React Router** and **TanStack Query** at the app root; keep **React Hook Form + Zod** as dependencies for upcoming forms.
4. Configure Vitest + Testing Library (jsdom) for smoke/unit tests.
5. Expose only `VITE_API_BASE_URL` for API base configuration (never secrets).
6. Use a single **npm** lockfile (`package-lock.json`).

## Consequences

- Positive: storefront/admin can share one Vite app and API client patterns; env remains configurable for EC2.
- Negative: Node must satisfy Vite engines (`^20.19.0 || >=22.12.0`); prefer Node 22 LTS if a future Vite release rejects Node 26.
