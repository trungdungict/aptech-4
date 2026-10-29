# ADR-013: SPA access/refresh token storage (Bearer JSON)

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P09-T02
- **Depends on:** ADR-005, ADR-012

## Context

Backend auth (`/api/v1/auth/*`) returns **access** and **refresh** JWTs in the JSON body and expects `Authorization: Bearer` on protected routes. There are **no** HttpOnly session cookies. The SPA must therefore hold the refresh token somehow if sessions should survive a page reload.

## Decision

1. Keep the **access token in memory only** (module + React state).
2. Persist the **refresh token** (and a non-sensitive user snapshot) in **`sessionStorage`** behind `features/auth/session/token-store.ts` — not `localStorage`.
3. On boot, call `POST /auth/refresh` to rotate and restore the access token; on failure, clear local session.
4. Coordinate concurrent refresh with a single in-flight promise; logout always clears local state and attempts `POST /auth/logout` without claiming success if that call fails.
5. Do not treat JWT payload decoding in the browser as authentication proof.

## Consequences

- Positive: matches ADR-005 Bearer model; isolated store can later move to BFF/cookie without rewriting pages.
- Negative: XSS can read `sessionStorage` refresh tokens — acceptable for school demo, **not** production-hardened. Prefer HttpOnly cookies or a BFF if hardening later.
