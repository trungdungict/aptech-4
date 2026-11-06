#!/usr/bin/env bash
# Opt-in Playwright E2E for customer storefront VI/EN against the nginx stack.
# Does not wipe volumes, edit .env, or invent catalog/promo rows.
#
# Usage:
#   make e2e-customer-bilingual
#   # or:
#   bash ./scripts/e2e-customer-bilingual.sh
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

: "${DEMO_CUSTOMER_EMAIL:?DEMO_CUSTOMER_EMAIL required}"
: "${DEMO_CUSTOMER_PASSWORD:?DEMO_CUSTOMER_PASSWORD required}"

echo "e2e-customer-bilingual: probing stack at $base"
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

npx playwright install chromium

export WEB_PORT
export PLAYWRIGHT_BASE_URL="$base"
export DEMO_CUSTOMER_EMAIL DEMO_CUSTOMER_PASSWORD
# Admin vars unused by this suite but harmless if present for shared config.
export DEMO_ADMIN_EMAIL="${DEMO_ADMIN_EMAIL:-}"
export DEMO_ADMIN_PASSWORD="${DEMO_ADMIN_PASSWORD:-}"

echo "e2e-customer-bilingual: running Playwright against $PLAYWRIGHT_BASE_URL"
npm run test:e2e -- e2e/customer-bilingual.spec.ts "$@"
echo "OK: e2e-customer-bilingual complete"
