#!/usr/bin/env bash
# Run Flutter / Shorebird commands inside the aptech4 mobile toolchain container.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.mobile.yml"
SERVICE="mobile-toolchain"
MOBILE_DIR="${ROOT_DIR}/mobile"
# Emulator → host; also the Dart `ApiConfig.defaultBaseUrl` (ADR-014).
DEFAULT_API_BASE_URL="http://10.0.2.2:8080/api/v1"
DEBUG_APK_REL="build/app/outputs/flutter-apk/app-debug.apk"
RELEASE_APK_REL="build/app/outputs/flutter-apk/app-release.apk"
RELEASE_AAB_REL="build/app/outputs/bundle/release/app-release.aab"

# Capture Make/CLI override before sourcing .env (empty → allow .env / default).
_API_BASE_URL_OVERRIDE="${API_BASE_URL:-}"

# Load gitignored .env (values never echoed). Make/CLI non-empty override wins.
if [[ -f "${ROOT_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${ROOT_DIR}/.env"
  set +a
fi

if [[ -n "${_API_BASE_URL_OVERRIDE}" ]]; then
  export API_BASE_URL="${_API_BASE_URL_OVERRIDE}"
fi

export HOST_UID="${HOST_UID:-$(id -u)}"
export HOST_GID="${HOST_GID:-$(id -g)}"
export FLUTTER_VERSION="${FLUTTER_VERSION:-3.47.8}"
export API_BASE_URL="${API_BASE_URL:-${DEFAULT_API_BASE_URL}}"

COMPOSE=(docker compose -f "${COMPOSE_FILE}" --profile mobile)

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

require_shorebird_token() {
  if [[ -z "${SHOREBIRD_TOKEN:-}" ]]; then
    die "SHOREBIRD_TOKEN is missing or empty. Add it to .env (see .env.example) or export it in your shell. Value is never logged."
  fi
}

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    die "docker CLI not found. Install/start Docker Desktop, then retry."
  fi
  if ! docker info >/dev/null 2>&1; then
    cat >&2 <<'EOF'
ERROR: Docker daemon is not reachable (permission denied or not running).

Fix on macOS:
  1. Open Docker Desktop and wait until it is running.
  2. Ensure your user can access the Docker socket
     (Docker Desktop → Settings → Advanced / Users).
  3. Re-run: docker info
  4. Then: make mobile-build-image

This environment does not auto-start or reinstall Docker Desktop.
EOF
    exit 1
  fi
}

require_project() {
  if [[ ! -f "${MOBILE_DIR}/pubspec.yaml" ]]; then
    die "No Flutter project at mobile/pubspec.yaml. Scaffold first (make mobile-create CONFIRM=yes) after toolchain works."
  fi
}

resolve_api_base_url() {
  # Public config only — safe to echo. Not a secret.
  printf '%s' "${API_BASE_URL:-${DEFAULT_API_BASE_URL}}"
}

warn_if_loopback_api_url() {
  local url="$1"
  case "$url" in
    *://localhost*|*://127.0.0.1*|*://\[::1\]*)
      echo "WARNING: API_BASE_URL uses a loopback host ($url)."
      echo "  On an Android emulator/device, localhost is the device itself — not your Mac."
      echo "  Prefer http://10.0.2.2:8080/api/v1 (emulator) or http://<LAN_IP>:8080/api/v1 (device)."
      ;;
  esac
}

verify_artifact() {
  local kind="$1"
  local rel="$2"
  local path="${MOBILE_DIR}/${rel}"
  if [[ ! -f "$path" ]]; then
    die "Expected ${kind} missing: mobile/${rel}"
  fi
  local size
  size="$(wc -c <"$path" | tr -d ' ')"
  if [[ "$size" -le 0 ]]; then
    die "${kind} is empty (0 bytes): mobile/${rel}"
  fi
  echo "OK: ${kind} present (${size} bytes) → mobile/${rel}"
}

# Back-compat name used by older call sites.
verify_apk() { verify_artifact APK "$1"; }

run_tool() {
  require_docker
  # Prefer pre-built image; build is explicit via mobile-build-image.
  if ! docker image inspect "aptech4-mobile-toolchain:${FLUTTER_VERSION}" >/dev/null 2>&1; then
    echo "Image aptech4-mobile-toolchain:${FLUTTER_VERSION} missing — building (first time may take several minutes)…"
    "${COMPOSE[@]}" build "${SERVICE}"
  fi
  local api
  api="$(resolve_api_base_url)"
  # Do not print env that may contain SHOREBIRD_TOKEN.
  "${COMPOSE[@]}" run --rm --no-deps \
    -e "API_BASE_URL=${api}" \
    -e "SHOREBIRD_TOKEN=${SHOREBIRD_TOKEN:-}" \
    -e "MOBILE_PREFLIGHT_MODE=${MOBILE_PREFLIGHT_MODE:-off}" \
    "${SERVICE}" "$@"
}

build_apk() {
  # $1 = debug|release
  local mode="$1"
  local api
  api="$(resolve_api_base_url)"
  warn_if_loopback_api_url "$api"
  echo "Building Flutter APK (${mode}) with --dart-define=API_BASE_URL=${api}"
  echo "(Shell API_BASE_URL is only a pass-through into dart-define; the app does not read OS env at runtime.)"
  require_project
  case "$mode" in
    debug)
      # Do not delete existing APK before a successful rebuild.
      MOBILE_PREFLIGHT_MODE=off run_tool flutter build apk --debug \
        --dart-define="API_BASE_URL=${api}"
      verify_artifact APK "${DEBUG_APK_REL}"
      ;;
    release)
      # Uses template debug signingConfig (see android/app/build.gradle.kts).
      # No production keystore is generated or required here.
      MOBILE_PREFLIGHT_MODE=off run_tool flutter build apk --release \
        --dart-define="API_BASE_URL=${api}"
      verify_artifact APK "${RELEASE_APK_REL}"
      ;;
    *)
      die "Unknown APK mode: ${mode}"
      ;;
  esac
}

build_aab() {
  local api
  api="$(resolve_api_base_url)"
  warn_if_loopback_api_url "$api"
  echo "Building Flutter App Bundle (release) with --dart-define=API_BASE_URL=${api}"
  echo "(Shell API_BASE_URL is only a pass-through into dart-define; the app does not read OS env at runtime.)"
  echo "Signing: debug keystore only (no production keystore invented)."
  require_project
  # Do not delete an existing AAB before a successful rebuild.
  MOBILE_PREFLIGHT_MODE=off run_tool flutter build appbundle --release \
    --dart-define="API_BASE_URL=${api}"
  verify_artifact AAB "${RELEASE_AAB_REL}"
}

cmd="${1:-}"
shift || true

case "$cmd" in
  ensure-docker) require_docker; echo "OK: Docker daemon reachable" ;;
  build-image)
    require_docker
    echo "Building aptech4-mobile-toolchain:${FLUTTER_VERSION} (linux/amd64)…"
    if ! "${COMPOSE[@]}" build "${SERVICE}"; then
      die "docker compose build failed for ${SERVICE} (see output above)."
    fi
    if ! docker image inspect "aptech4-mobile-toolchain:${FLUTTER_VERSION}" >/dev/null 2>&1; then
      die "Build reported success but image aptech4-mobile-toolchain:${FLUTTER_VERSION} is missing."
    fi
    echo "OK: image aptech4-mobile-toolchain:${FLUTTER_VERSION} is present."
    ;;
  doctor)
    # flutter doctor often exits non-zero with warnings; report status explicitly.
    # shorebird doctor must not be masked — auth/network failures are real.
    MOBILE_PREFLIGHT_MODE=off run_tool bash -lc '
      set +e
      echo "=== flutter doctor -v ==="
      flutter doctor -v
      fd=$?
      echo "flutter_doctor_exit=$fd"
      echo
      echo "=== Android SDK packages (pinned) ==="
      sdkmanager --list_installed 2>/dev/null | grep -E "platforms;android-36|build-tools;36|platform-tools" || true
      echo
      echo "=== shorebird --version ==="
      shorebird --version
      echo
      echo "=== shorebird doctor ==="
      shorebird doctor
      sd=$?
      echo "shorebird_doctor_exit=$sd"
      # Fail the Make target if Shorebird doctor fails; Flutter doctor warnings alone are not fatal here.
      exit $sd
    '
    ;;
  versions)
    MOBILE_PREFLIGHT_MODE=off run_tool bash -lc 'flutter --version; dart --version; shorebird --version; java -version 2>&1 | head -1'
    ;;
  shorebird-auth)
    require_shorebird_token
    # Official auth probe without dumping env. Never enable set -x here.
    MOBILE_PREFLIGHT_MODE=off run_tool bash -lc '
      set +e
      echo "SHOREBIRD_TOKEN: set (value hidden)"
      shorebird doctor
      sd=$?
      echo "shorebird_doctor_exit=$sd"
      if shorebird account --help >/dev/null 2>&1; then
        echo "=== shorebird account ==="
        shorebird account
        ac=$?
        echo "shorebird_account_exit=$ac"
        if [ "$ac" -ne 0 ]; then exit "$ac"; fi
      else
        echo "NOTE: shorebird account not available on this CLI; relying on shorebird doctor + init/release auth."
      fi
      exit $sd
    '
    ;;
  pub-get)
    require_project
    MOBILE_PREFLIGHT_MODE=off run_tool flutter pub get
    ;;
  analyze)
    require_project
    MOBILE_PREFLIGHT_MODE=off run_tool flutter analyze
    ;;
  test)
    require_project
    MOBILE_PREFLIGHT_MODE=off run_tool flutter test
    ;;
  build-android|build-apk)
    # Alias: build-apk == debug APK (P12-T02). Does not install or publish.
    build_apk debug
    ;;
  build-release-apk)
    build_apk release
    ;;
  build-release-aab)
    build_aab
    ;;
  shorebird-workflow)
    # Read-only readiness: CLI help + config presence. Does NOT publish.
    require_project
    if [[ ! -f "${MOBILE_DIR}/shorebird.yaml" ]]; then
      die "mobile/shorebird.yaml missing. Run: make mobile-shorebird-init (once)."
    fi
    echo "OK: mobile/shorebird.yaml present (app_id configured; not a secret)."
    echo "Shorebird publish is gated: make mobile-shorebird-release|patch CONFIRM=yes"
    echo "This target does not upload a release or patch."
    MOBILE_PREFLIGHT_MODE=off run_tool bash -lc '
      set -e
      echo "=== shorebird --version ==="
      shorebird --version
      echo
      echo "=== shorebird release android --help (excerpt) ==="
      shorebird release android --help 2>&1 | head -40
      echo
      echo "=== shorebird patch android --help (excerpt) ==="
      shorebird patch android --help 2>&1 | head -40
      echo
      echo "OK: Shorebird CLI release/patch commands are available (no publish performed)."
    '
    ;;
  create)
    confirm="${1:-}"
    if [[ "$confirm" != "yes" ]]; then
      cat <<'EOF'
Refusing flutter create without CONFIRM=yes.
After toolchain validation:
  make mobile-create CONFIRM=yes

This uses existing mobile/ as the target and will not run if pubspec.yaml already exists.
EOF
      exit 1
    fi
    if [[ -f "${MOBILE_DIR}/pubspec.yaml" ]]; then
      die "mobile/pubspec.yaml already exists — refusing to overwrite."
    fi
    require_docker
    # Preserve README / .fvmrc.example; flutter create into non-empty dir needs --overwrite carefully.
    # Use flutter create . which merges into existing directory without deleting unrelated files when possible.
    MOBILE_PREFLIGHT_MODE=off run_tool flutter create . \
      --project-name aptech4_mobile \
      --org com.aptech.aptech4 \
      --platforms=android
    echo "OK: Flutter project scaffolded under mobile/ (Android only)."
    ;;
  shorebird-init)
    require_project
    require_shorebird_token
    if [[ -f "${MOBILE_DIR}/shorebird.yaml" ]]; then
      echo "OK: shorebird.yaml already present — skipping init (no overwrite)."
      exit 0
    fi
    echo "Running one-time shorebird init (non-interactive where supported)…"
    # Prefer --force for CI-like bootstrap; do not print token.
    MOBILE_PREFLIGHT_MODE=off run_tool bash -lc 'shorebird init --help >/dev/null 2>&1; if shorebird init --help 2>&1 | grep -q -- "--force"; then shorebird init --force; else shorebird init; fi'
    ;;
  shorebird-release)
    require_project
    if [[ "${CONFIRM:-}" != "yes" ]]; then
      die "Refusing real Shorebird release without CONFIRM=yes. Example: make mobile-shorebird-release CONFIRM=yes"
    fi
    require_shorebird_token
    MOBILE_PREFLIGHT_MODE=off run_tool shorebird release android \
      --flutter-version="${FLUTTER_VERSION}" \
      --dart-define="API_BASE_URL=$(resolve_api_base_url)"
    ;;
  shorebird-patch)
    require_project
    if [[ "${CONFIRM:-}" != "yes" ]]; then
      die "Refusing real Shorebird patch without CONFIRM=yes. Example: make mobile-shorebird-patch CONFIRM=yes"
    fi
    require_shorebird_token
    # Do not pass --allow-asset-diffs / native overrides automatically.
    MOBILE_PREFLIGHT_MODE=off run_tool shorebird patch android \
      --dart-define="API_BASE_URL=$(resolve_api_base_url)"
    ;;
  shell)
    MOBILE_PREFLIGHT_MODE=off run_tool bash
    ;;
  *)
    cat <<EOF
Usage: $0 {ensure-docker|build-image|versions|doctor|shorebird-auth|pub-get|analyze|test|build-android|build-apk|build-release-apk|build-release-aab|shorebird-workflow|create|shorebird-init|shorebird-release|shorebird-patch|shell}
EOF
    exit 2
    ;;
esac
