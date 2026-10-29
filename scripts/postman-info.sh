#!/usr/bin/env bash
# Print local Compose + Postman developer info.
# Demo passwords come ONLY from documented local-only DEMO_SEED values (not from .env).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WEB_PORT="${WEB_PORT:-8088}"

# Documented local-only demo credentials (docs/DEMO_SEED.md / .env.example). Never read .env here.
DEMO_CUSTOMER_EMAIL='customer@aptech4.local'
DEMO_CUSTOMER_PASSWORD='CustomerDemo_change_me!'
DEMO_ADMIN_EMAIL='admin@aptech4.local'
DEMO_ADMIN_PASSWORD='AdminDemo_change_me!'

cat <<EOF
════════════════════════════════════════════════════════════
 Aptech-4 — local Postman / stack info
════════════════════════════════════════════════════════════

LOCAL URLS
  Web:              http://localhost:${WEB_PORT}
  API base (Nginx): http://localhost:${WEB_PORT}/api/v1
  Health:           http://localhost:${WEB_PORT}/api/v1/health
  API loopback:     http://127.0.0.1:8080   (debug; Compose API_PUBLISH)
  OpenAPI JSON:     http://127.0.0.1:8080/v3/api-docs
  Swagger UI:       http://127.0.0.1:8080/swagger-ui/index.html
  (Swagger/OpenAPI are NOT proxied by local nginx on :${WEB_PORT})

DEMO ACCOUNTS (local-only — from docs/DEMO_SEED.md)
  WARNING: Demo credentials only. Never use in production.
  CUSTOMER email:    ${DEMO_CUSTOMER_EMAIL}
  CUSTOMER password: ${DEMO_CUSTOMER_PASSWORD}
  ADMIN email:       ${DEMO_ADMIN_EMAIL}
  ADMIN password:    ${DEMO_ADMIN_PASSWORD}

POSTMAN
  Collection:   ${ROOT}/postman/APTECH-4-Local.postman_collection.json
  Environment:  ${ROOT}/postman/APTECH-4-Local.postman_environment.json
  Environment baseUrl: http://localhost:${WEB_PORT}/api/v1
  Set customerPassword / adminPassword in Postman from the DEMO ACCOUNTS above
  (committed environment uses REPLACE_WITH_* placeholders).
  Do not commit Postman exports that contain live access/refresh tokens.

COMMON COMMANDS
  make deploy-local
  make health-local
  make smoke-local
  make smoke-demo
  make seed-check
  make seed-demo
  make reset-demo-dry
  make reset-demo CONFIRM=yes
  make postman-export
  make postman-validate
  make postman-info
  make postman-help
  make postman-smoke   # optional Newman smoke if npx newman available

════════════════════════════════════════════════════════════
EOF
