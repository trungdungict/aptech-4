#!/usr/bin/env bash
# Lightweight health/preflight for the mobile toolchain container.
# Never prints SHOREBIRD_TOKEN or other secrets.
set -euo pipefail

mode="${MOBILE_PREFLIGHT_MODE:-quick}"

ensure_cache_dirs() {
  # Named volumes may be root-owned on first create; developer has passwordless sudo.
  local d
  for d in \
    "${HOME}/.gradle" \
    "${HOME}/.pub-cache" \
    "${HOME}/.shorebird/bin/cache" \
    "${HOME}/.android"; do
    if [[ -e "$d" ]] && [[ ! -w "$d" ]]; then
      sudo chown -R "$(id -u):$(id -g)" "$d" 2>/dev/null || true
    fi
    mkdir -p "$d" 2>/dev/null || sudo mkdir -p "$d"
    if [[ ! -w "$d" ]]; then
      sudo chown -R "$(id -u):$(id -g)" "$d" 2>/dev/null || true
    fi
  done
}

ensure_cache_dirs

# Skip banner when Make runs concrete tool commands.
if [[ "$mode" == "off" ]]; then
  if [[ "$#" -gt 0 ]]; then
    exec "$@"
  fi
  exec bash
fi

echo "=== aptech4 mobile toolchain preflight (${mode}) ==="
echo "user=$(id -un) uid=$(id -u) gid=$(id -g)"

if ! command -v flutter >/dev/null 2>&1; then
  echo "FAIL: flutter not on PATH"
  exit 1
fi
if ! command -v dart >/dev/null 2>&1; then
  echo "FAIL: dart not on PATH"
  exit 1
fi
if ! command -v java >/dev/null 2>&1; then
  echo "FAIL: java not on PATH"
  exit 1
fi

flutter --version | head -5
dart --version
java -version 2>&1 | head -1

if [[ -n "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}" ]]; then
  echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
else
  echo "FAIL: ANDROID_SDK_ROOT/ANDROID_HOME unset"
  exit 1
fi

if command -v shorebird >/dev/null 2>&1; then
  shorebird --version || true
else
  echo "WARN: shorebird not on PATH (OTA commands unavailable)"
fi

if [[ -n "${SHOREBIRD_TOKEN:-}" ]]; then
  echo "SHOREBIRD_TOKEN: set (value hidden)"
else
  echo "SHOREBIRD_TOKEN: unset (required for shorebird release/patch against Shorebird Cloud)"
fi

echo "=== preflight done ==="

if [[ "$#" -gt 0 ]]; then
  exec "$@"
fi
