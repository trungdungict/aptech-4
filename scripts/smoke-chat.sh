#!/usr/bin/env bash
# Authenticated chat health READY + one completion via stack nginx.
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
email="${DEMO_CUSTOMER_EMAIL:-customer@aptech4.local}"
pass="${DEMO_CUSTOMER_PASSWORD:?DEMO_CUSTOMER_PASSWORD required}"

echo "smoke-chat: login + /api/v1/chat/health + short message"
code="$(curl -sS -o /tmp/aptech4-chat-login.json -w '%{http_code}' \
	-X POST "$base/api/v1/auth/login" \
	-H 'Content-Type: application/json' \
	-d "{\"email\":\"$email\",\"password\":\"$pass\"}")"
if [[ "$code" != "200" ]]; then
	echo "ERROR: chat login HTTP $code" >&2
	cat /tmp/aptech4-chat-login.json >&2 || true
	exit 1
fi
token="$(python3 -c 'import json; print(json.load(open("/tmp/aptech4-chat-login.json"))["accessToken"])')"

code="$(curl -sS -o /tmp/aptech4-chat-health.json -w '%{http_code}' \
	-H "Authorization: Bearer $token" "$base/api/v1/chat/health")"
if [[ "$code" != "200" ]]; then
	echo "ERROR: chat health HTTP $code" >&2
	cat /tmp/aptech4-chat-health.json >&2 || true
	exit 1
fi
python3 - <<'PY'
import json
h = json.load(open("/tmp/aptech4-chat-health.json"))
print("chat health", h)
if h.get("status") != "READY" or not h.get("enabled") or not h.get("modelConfigured"):
    raise SystemExit(
        "ERROR: chat must be READY (got status=%r enabled=%r modelConfigured=%r detail=%r)"
        % (h.get("status"), h.get("enabled"), h.get("modelConfigured"), h.get("detail"))
    )
PY

code="$(curl -sS -o /tmp/aptech4-chat-msg.json -w '%{http_code}' --max-time 120 \
	-X POST "$base/api/v1/chat/messages" \
	-H "Authorization: Bearer $token" \
	-H 'Content-Type: application/json' \
	-d '{"message":"Reply with exactly: OK","language":"en"}')"
if [[ "$code" != "200" ]]; then
	echo "ERROR: chat message HTTP $code (Ollama model missing or unreachable?)" >&2
	cat /tmp/aptech4-chat-msg.json >&2 || true
	exit 1
fi
python3 - <<'PY'
import json
m = json.load(open("/tmp/aptech4-chat-msg.json"))
text = m.get("assistantMessage") or m.get("reply") or m.get("message") or m.get("content") or str(m)
print("chat reply preview:", str(text)[:240])
if not str(text).strip():
    raise SystemExit("ERROR: empty chat reply")
PY

echo "OK: smoke-chat (READY + completion)"
