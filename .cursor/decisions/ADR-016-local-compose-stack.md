# ADR-016: Local production-like Compose stack (API + web)

- **Status:** Accepted
- **Date:** 2026-10-29
- **Related tasks:** P14-T01, P14-T02

## Context

Developers need a full API + static web + Postgres + Redis stack without AWS. Vite embeds `VITE_API_BASE_URL` at **build** time, so a production image cannot rely on runtime env to retarget the API. Hardcoding `localhost:8080` into the web image breaks same-host nginx and future EC2 demos.

## Decision

1. Compose **profile `stack`** adds `api` (Spring Boot multi-stage image) and `web` (Vite build + nginx). Default profile remains Postgres + Redis only (`make up-deps`).
2. Browser uses **same-origin** `VITE_API_BASE_URL=/api/v1`. nginx (`deploy/nginx/local-stack.conf`) proxies `/api/` → `http://api:8080/api/`.
3. API waits for healthy Postgres (and Redis). Health: `GET /api/v1/health`. Web publishes `WEB_PORT` (default **8088**). API may publish loopback `API_PUBLISH` (default `127.0.0.1:8080`) for debug only.
4. Named volumes `aptech4_pgdata` / `aptech4_redisdata` persist across stop/start. Make never runs `down -v`. Seed is **not** auto-enabled (`APTECH_SEED_ENABLED=false`).
5. Secrets stay in gitignored `.env`; Docker build contexts exclude `.env` via `.dockerignore`.
6. API container CORS allow-list is a **literal** Compose value including `http://localhost:8088` (and loopback). Do not interpolate host `CORS_ALLOWED_ORIGINS` alone — browsers send `Origin` on JSON POSTs even through same-host nginx; missing origin → Spring **403 Invalid CORS request**.

## Alternatives considered

1. Separate hostnames/ports + absolute `http://api:8080` in the browser — rejected: browser cannot resolve Docker DNS; CORS and mixed ports complicate demos.
2. Runtime config injection into SPA — rejected for now: Vite does not support it without extra plumbing; path-absolute base is enough for nginx/EC2.

## Consequences

- Positive: one origin for UI+API; no localhost baked into the stack web image; deps-only workflow unchanged.
- Negative: changing API path requires rebuilding the web image; EC2 still needs TLS/nginx overlays (P15).
