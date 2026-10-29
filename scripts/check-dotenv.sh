#!/usr/bin/env bash
# Verify required .env keys are present without printing secret values.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"

# Keys required for local demo / API (values never printed).
REQUIRED_KEYS=(
  JWT_ACCESS_SECRET
  JWT_REFRESH_SECRET
  DEMO_ADMIN_PASSWORD
  DEMO_CUSTOMER_PASSWORD
  POSTGRES_PASSWORD
)

# Optional for everyday Flutter; required for Shorebird OTA Make targets.
OPTIONAL_KEYS=(
  SHOREBIRD_TOKEN
  API_BASE_URL
  VITE_API_BASE_URL
  FLUTTER_VERSION
)

have_key() {
  local key="$1"
  [[ -f "$ENV_FILE" ]] || return 1
  # Match KEY=... with optional optional export / spaces; ignore comments.
  grep -Eq "^[[:space:]]*(export[[:space:]]+)?${key}=" "$ENV_FILE"
}

value_nonempty() {
  local key="$1"
  local line raw
  line="$(grep -E "^[[:space:]]*(export[[:space:]]+)?${key}=" "$ENV_FILE" | tail -n1 || true)"
  [[ -n "$line" ]] || return 1
  raw="${line#*=}"
  # Strip surrounding single/double quotes
  if [[ "$raw" =~ ^\"(.*)\"$ ]]; then raw="${BASH_REMATCH[1]}"; fi
  if [[ "$raw" =~ ^\'(.*)\'$ ]]; then raw="${BASH_REMATCH[1]}"; fi
  [[ -n "$raw" ]]
}

status_for() {
  local key="$1"
  if ! have_key "$key"; then
    printf '%s: MISSING\n' "$key"
    return 1
  fi
  if value_nonempty "$key"; then
    printf '%s: set\n' "$key"
    return 0
  fi
  printf '%s: empty\n' "$key"
  return 1
}

echo "=== check-env (.env presence; values hidden) ==="
if [[ ! -f "$ENV_FILE" ]]; then
  echo "FAIL: $ENV_FILE not found"
  echo "Create it with: make setup   # copies .env.example only when missing"
  echo "Then set secrets locally (never commit .env)."
  exit 1
fi
echo "OK: .env file present"

rc=0
echo "-- required --"
for k in "${REQUIRED_KEYS[@]}"; do
  status_for "$k" || rc=1
done

echo "-- optional / feature --"
for k in "${OPTIONAL_KEYS[@]}"; do
  if have_key "$k" && value_nonempty "$k"; then
    printf '%s: set\n' "$k"
  elif have_key "$k"; then
    printf '%s: empty (set before Shorebird OTA / client URL use)\n' "$k"
  else
    printf '%s: unset (add to .env when needed)\n' "$k"
  fi
done

if [[ "$rc" -ne 0 ]]; then
  echo "FAIL: one or more required variables are missing or empty (values not shown)."
  exit 1
fi
echo "OK: required environment variables are present."
