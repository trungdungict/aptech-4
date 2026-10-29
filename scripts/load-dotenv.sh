#!/usr/bin/env bash
# Source repo-root .env into the current shell (must be sourced, not executed).
# Usage: source scripts/load-dotenv.sh
# Never echoes secret values.

_APTECH_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
_APTECH_ENV="${ENV_FILE:-$_APTECH_ROOT/.env}"

if [[ -f "$_APTECH_ENV" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$_APTECH_ENV"
  set +a
fi

unset _APTECH_ROOT _APTECH_ENV
