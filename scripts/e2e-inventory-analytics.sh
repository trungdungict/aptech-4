#!/usr/bin/env bash
# Opt-in Playwright E2E for Admin Inventory + Analytics against the nginx stack.
# Does not wipe volumes, edit .env, or invent analytics/inventory rows.
#
# Usage:
#   make e2e-inventory-analytics
#   # or:
#   bash ./scripts/e2e-inventory-analytics.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f .env ]]; then
	set -a
	# shellcheck disable=SC1091
	source ./.env
	set +a
fi

WEB_PORT="${WEB_PORT:-8088}"
base="http://127.0.0.1:${WEB_PORT}"

: "${DEMO_ADMIN_EMAIL:?DEMO_ADMIN_EMAIL required}"
: "${DEMO_ADMIN_PASSWORD:?DEMO_ADMIN_PASSWORD required}"
: "${DEMO_CUSTOMER_EMAIL:?DEMO_CUSTOMER_EMAIL required}"
: "${DEMO_CUSTOMER_PASSWORD:?DEMO_CUSTOMER_PASSWORD required}"

echo "e2e-inventory-analytics: probing stack at $base"
code="$(curl -sS -o /dev/null -w '%{http_code}' "$base/api/v1/health" || true)"
if [[ "$code" != "200" ]]; then
	echo "ERROR: health HTTP $code — start the stack (make deploy-local / up-all) before E2E."
	echo "Tests were NOT executed."
	exit 1
fi

cd frontend
if [[ ! -d node_modules/@playwright/test ]]; then
	echo "Installing frontend deps (includes @playwright/test)…"
	npm install
fi

# Ensure Chromium is present for this Playwright version (no global browser install assumed).
npx playwright install chromium

export WEB_PORT
export PLAYWRIGHT_BASE_URL="$base"
export DEMO_ADMIN_EMAIL DEMO_ADMIN_PASSWORD DEMO_CUSTOMER_EMAIL DEMO_CUSTOMER_PASSWORD

echo "e2e-inventory-analytics: running Playwright against $PLAYWRIGHT_BASE_URL"
npm run test:e2e -- "$@"
echo "OK: e2e-inventory-analytics complete"
