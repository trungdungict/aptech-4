# ADR-005: JWT access/refresh security baseline

- **Status:** Accepted
- **Date:** 2026-10-28
- **Related tasks:** P04-T01, P04-T02, P04-T03

## Context

Need a stateless API auth foundation for `/api/v1/**` with separate access and refresh lifecycles, fail-fast secrets, and no session cookies for the school demo.

## Decision

- Library: **JJWT 0.13.0** (`jjwt-api` + `jjwt-impl` + `jjwt-jackson`).
- Algorithm: **HS256** with independent access and refresh secrets (min **32** UTF-8 bytes each; must differ).
- Access JWT claims: `sub` (user id), `email`, `roles`, `iss`, `iat`, `exp`, `token_use=access`.
- Refresh JWT (issued later in P04-T03): same shape with `token_use=refresh`, signed with **refresh** secret; API filter accepts **access** tokens only.
- Persist refresh tokens as **hash only** in DB (schema already has `refresh_tokens.token_hash`).
- Stateless sessions; **CSRF disabled** because clients use `Authorization: Bearer` (revisit if cookie auth is added).
- CORS: explicit origin list from env; never `*` with credentials.
- Authorities: Spring `ROLE_` + `RoleCode` name (`ROLE_CUSTOMER`, `ROLE_ADMIN`).
- Missing/short/identical secrets → **fail startup** (no weak defaults).

## Alternatives considered

1. Asymmetric RS256 — stronger key distribution, heavier for semester demo EC2; deferred.
2. Spring Session / cookies — rejected for mobile+SPA Bearer consistency.
3. Single shared secret for access+refresh — rejected (refresh replay risk).

## Consequences

- Positive: clear token separation; testable filter chain; OpenAPI Bearer scheme stays valid.
- Negative: HS256 secret rotation is manual; demo must keep secrets out of git.
- Follow-ups: P04-T02 login/register issues access JWT; P04-T03 refresh rotation/reuse detection (implemented: SHA-256 hash store, rotate with row lock, reuse of revoked token revokes all active sessions for that user).
