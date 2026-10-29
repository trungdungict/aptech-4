# Deploy artifacts

Nginx configs and EC2 helpers live here. See `.cursor/plans/07-ec2-deployment.md`.

## Local Compose stack (P14)

| File | Role |
|------|------|
| `nginx/local-stack.conf` | SPA + `location /api/` → `http://api:8080/api/` + `location /ws` WebSocket upgrade (ADR-018) |

Used by `frontend/Dockerfile` (Compose profile `stack`). Browser base URL is path-absolute `/api/v1` (ADR-016). EC2 TLS / public bind overlays come in P15 — do not publish Postgres/Redis publicly there.

### Demo seed with the local stack

Use root Make targets (`seed-check`, `seed-demo`, `reset-demo-dry`, `reset-demo CONFIRM=yes`, `smoke-demo`). Details: [`docs/DEMO_SEED.md`](../docs/DEMO_SEED.md), ADR-010/011. Seed is never part of `deploy-local` / container boot.

### Postman

Local Collection/Environment: [`postman/`](../postman/). `make postman-export` / `postman-info` / `postman-help`. API base for Postman is `http://localhost:8088/api/v1`. OpenAPI/Swagger remain on API loopback `:8080` (not proxied by this nginx conf).
