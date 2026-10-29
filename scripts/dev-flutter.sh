#!/usr/bin/env bash
# Aptech-4 Flutter/FVM helpers for the root Makefile.
# Safe to source; does not mutate the host unless an explicit install_* function is called.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MOBILE_DIR="${MOBILE_DIR:-$ROOT_DIR/mobile}"
FVMRC="${FVMRC:-$MOBILE_DIR/.fvmrc}"
FVM_CONFIG="${FVM_CONFIG:-$MOBILE_DIR/.fvm/fvm_config.json}"

# Target platform for this monorepo (P11–P12): Android customer app only.
APTECH_MOBILE_PLATFORMS="${APTECH_MOBILE_PLATFORMS:-android}"

os_name() {
  uname -s 2>/dev/null || echo unknown
}

have() {
  command -v "$1" >/dev/null 2>&1
}

print_ok() { printf 'OK: %s\n' "$*"; }
print_warn() { printf 'WARN: %s\n' "$*"; }
print_fail() { printf 'FAIL: %s\n' "$*"; }

# Read pinned Flutter version from mobile/.fvmrc or mobile/.fvm/fvm_config.json.
# Does not invent a version.
pinned_flutter_version() {
  if [[ -f "$FVMRC" ]]; then
    local line compact
    while IFS= read -r line || [[ -n "$line" ]]; do
      # skip comments / blanks
      [[ "$line" =~ ^[[:space:]]*# ]] && continue
      [[ "$line" =~ ^[[:space:]]*$ ]] && continue
      # trim
      line="${line#"${line%%[![:space:]]*}"}"
      line="${line%"${line##*[![:space:]]}"}"
      compact="${line//[[:space:]]/}"
      if [[ "$compact" == \{* ]]; then
        if [[ "$compact" =~ \"flutter\":\"([^\"]+)\" ]]; then
          printf '%s\n' "${BASH_REMATCH[1]}"
          return 0
        fi
      else
        line="${line%\"}"
        line="${line#\"}"
        if [[ -n "$line" ]]; then
          printf '%s\n' "$line"
          return 0
        fi
      fi
    done <"$FVMRC"
  fi
  if [[ -f "$FVM_CONFIG" ]]; then
    local conf
    conf="$(tr -d '[:space:]' <"$FVM_CONFIG")"
    if [[ "$conf" =~ \"flutterSdkVersion\":\"([^\"]+)\" ]]; then
      printf '%s\n' "${BASH_REMATCH[1]}"
      return 0
    fi
  fi
  return 1
}

require_pinned_version() {
  local ver
  if ver="$(pinned_flutter_version)"; then
    printf '%s\n' "$ver"
    return 0
  fi
  print_fail "No Flutter version pinned for this repo."
  echo "Configure FVM in the mobile app directory, then re-run:"
  echo "  cd mobile"
  echo "  # choose an explicit stable version (do not leave unset):"
  echo "  fvm use <version> --force"
  echo "  # or create mobile/.fvmrc containing only the version, e.g. 3.24.5"
  echo "See mobile/README.md and .fvmrc.example."
  return 1
}

mobile_project_ready() {
  [[ -f "$MOBILE_DIR/pubspec.yaml" ]]
}

fvm_bin() {
  if have fvm; then
    command -v fvm
    return 0
  fi
  return 1
}

# Prefer FVM-managed flutter/dart; never assume a global flutter is correct.
flutter_cmd() {
  if have fvm && [[ -d "$MOBILE_DIR" ]]; then
    echo "fvm flutter"
    return 0
  fi
  return 1
}

dart_cmd() {
  if have fvm && [[ -d "$MOBILE_DIR" ]]; then
    echo "fvm dart"
    return 0
  fi
  return 1
}

run_in_mobile() {
  if ! mobile_project_ready; then
    print_fail "No Flutter project at $MOBILE_DIR (missing pubspec.yaml)."
    echo "Scaffold the app first (P11-T01), then re-run. Do not invent a project via Make."
    return 1
  fi
  (cd "$MOBILE_DIR" && "$@")
}

check_make() {
  if have make; then
    print_ok "make ($(make --version 2>/dev/null | head -1))"
    return 0
  fi
  print_fail "make not found"
  return 1
}

check_git() {
  if have git; then
    print_ok "git ($(git --version 2>/dev/null))"
    return 0
  fi
  print_fail "git not found"
  return 1
}

check_brew() {
  local os
  os="$(os_name)"
  if [[ "$os" != Darwin ]]; then
    print_ok "Homebrew not required on $(os_name) for Makefile install path"
    return 0
  fi
  if have brew; then
    print_ok "Homebrew ($(brew --version 2>/dev/null | head -1))"
    return 0
  fi
  print_fail "Homebrew not found (macOS install path uses brew for FVM)"
  echo "  Install manually: https://brew.sh"
  echo "  Makefile will not install Homebrew without CONFIRM_BREW=yes"
  return 1
}

check_fvm() {
  if have fvm; then
    print_ok "fvm ($(fvm --version 2>/dev/null | head -1))"
    return 0
  fi
  print_fail "fvm not found"
  echo "  macOS: brew tap leoafarias/fvm && brew install fvm"
  echo "  or: dart pub global activate fvm  (requires a Dart SDK)"
  echo "  Then: make install-tools CONFIRM=yes"
  return 1
}

check_java() {
  if have java; then
    print_ok "java ($(java -version 2>&1 | head -1))"
    return 0
  fi
  print_fail "java not found (needed for Android builds)"
  echo "  Recommended: JDK 17+ (repo backend uses JDK 21 via Homebrew openjdk@21)"
  return 1
}

check_android_sdk() {
  local sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -z "$sdk" ]]; then
    # common macOS Android Studio locations
    for candidate in \
      "$HOME/Library/Android/sdk" \
      "$HOME/Android/Sdk"; do
      if [[ -d "$candidate" ]]; then
        sdk="$candidate"
        break
      fi
    done
  fi
  if [[ -n "$sdk" && -d "$sdk" ]]; then
    print_ok "Android SDK directory: $sdk"
    if [[ -x "$sdk/cmdline-tools/latest/bin/sdkmanager" ]] || [[ -x "$sdk/tools/bin/sdkmanager" ]]; then
      print_ok "Android cmdline-tools present"
    else
      print_warn "Android cmdline-tools (sdkmanager) not found under $sdk"
    fi
    if [[ -d "$sdk/platform-tools" ]]; then
      print_ok "Android platform-tools present"
    else
      print_warn "Android platform-tools missing"
    fi
    return 0
  fi
  print_fail "Android SDK not found (set ANDROID_SDK_ROOT or install Android Studio / cmdline-tools)"
  return 1
}

check_android_licenses() {
  local sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  [[ -z "$sdk" ]] && sdk="$HOME/Library/Android/sdk"
  if [[ ! -d "$sdk/licenses" ]]; then
    print_warn "Android licenses directory missing — accept with: flutter doctor --android-licenses (via FVM after setup)"
    return 0
  fi
  print_ok "Android licenses directory present ($sdk/licenses)"
  return 0
}

check_emulator_optional() {
  if have emulator || [[ -x "${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}/emulator/emulator" ]]; then
    print_ok "Android emulator binary available (optional)"
  else
    print_warn "Android emulator not found (optional unless you run local AVDs)"
  fi
  return 0
}

check_tools() {
  local rc=0
  echo "=== check-tools (read-only) ==="
  echo "Host: $(os_name) $(uname -m 2>/dev/null || true)"
  check_make || rc=1
  check_git || rc=1
  if [[ "$(os_name)" == Darwin ]]; then
    check_brew || rc=1
  else
    print_ok "Non-macOS: use distro packages / documented FVM install (brew path skipped)"
  fi
  check_fvm || rc=1
  echo "Platform focus: $APTECH_MOBILE_PLATFORMS (iOS/web not required by Aptech-4 plan)"
  if [[ "$APTECH_MOBILE_PLATFORMS" == *android* ]]; then
    check_java || rc=1
  fi
  return "$rc"
}

check_env() {
  local rc=0
  echo "=== check-env (read-only) ==="
  check_tools || rc=1

  if ! mobile_project_ready; then
    print_fail "Flutter project missing: $MOBILE_DIR/pubspec.yaml"
    print_warn "P11-T01 scaffold not present — environment checks for pub get/analyze cannot pass yet."
    rc=1
  else
    print_ok "Flutter project: $MOBILE_DIR/pubspec.yaml"
  fi

  local ver=""
  if ver="$(pinned_flutter_version)"; then
    print_ok "Pinned Flutter (FVM): $ver"
  else
    print_fail "Pinned Flutter version not configured (mobile/.fvmrc or .fvm/fvm_config.json)"
    rc=1
  fi

  if have fvm; then
    if ver="$(pinned_flutter_version 2>/dev/null || true)"; then
      if [[ -n "$ver" ]]; then
        if (cd "$MOBILE_DIR" && fvm list 2>/dev/null | grep -q "$ver") || [[ -d "$HOME/fvm/versions/$ver" ]] || [[ -d "$MOBILE_DIR/.fvm/flutter_sdk" ]]; then
          print_ok "FVM appears to have SDK access for pinned version (or project link)"
        else
          print_warn "Pinned $ver may not be installed yet — run: make install-tools CONFIRM=yes"
        fi
      fi
    fi
    if mobile_project_ready; then
      if (cd "$MOBILE_DIR" && fvm flutter --version >/dev/null 2>&1); then
        print_ok "fvm flutter responds in mobile/"
        (cd "$MOBILE_DIR" && fvm flutter --version | head -3) || true
      else
        print_fail "fvm flutter failed in mobile/ — install pinned SDK: fvm install && fvm use"
        rc=1
      fi
      if (cd "$MOBILE_DIR" && fvm dart --version >/dev/null 2>&1); then
        print_ok "fvm dart: $(cd "$MOBILE_DIR" && fvm dart --version 2>&1 | head -1)"
      else
        print_fail "fvm dart not available"
        rc=1
      fi
    fi
  fi

  if [[ "$APTECH_MOBILE_PLATFORMS" == *android* ]]; then
    check_android_sdk || rc=1
    check_android_licenses
    check_emulator_optional
  fi

  echo "iOS/Xcode/CocoaPods: skipped (not a project target)."
  echo "Flutter web: skipped (not a project target)."
  return "$rc"
}

status_report() {
  echo "=== status ==="
  echo "ROOT:   $ROOT_DIR"
  echo "MOBILE: $MOBILE_DIR"
  echo "OS:     $(os_name) $(uname -m 2>/dev/null || true)"
  printf 'make:   '; have make && echo present || echo missing
  printf 'git:    '; have git && echo present || echo missing
  printf 'brew:   '; have brew && echo present || echo missing
  printf 'fvm:    '; have fvm && fvm --version 2>/dev/null | head -1 || echo missing
  printf 'java:   '; have java && java -version 2>&1 | head -1 || echo missing
  if ver="$(pinned_flutter_version 2>/dev/null)"; then
    echo "pinned Flutter: $ver"
  else
    echo "pinned Flutter: (not configured)"
  fi
  if mobile_project_ready; then
    echo "pubspec: present"
  else
    echo "pubspec: MISSING (scaffold required)"
  fi
  if [[ -f "$MOBILE_DIR/pubspec.lock" ]]; then
    echo "pubspec.lock: present"
  else
    echo "pubspec.lock: absent"
  fi
  # Overall readiness (non-zero if not ready)
  local ready=1
  have fvm || ready=0
  mobile_project_ready || ready=0
  pinned_flutter_version >/dev/null 2>&1 || ready=0
  if [[ "$ready" -eq 1 ]]; then
    print_ok "Project readiness: tools+pin+pubspec look present (still run check-env / doctor)"
  else
    print_warn "Project readiness: incomplete — see make check-env"
  fi
}

install_tools() {
  local confirm="${1:-}"
  if [[ "$confirm" != "yes" ]]; then
    echo "install-tools is gated. Review what would run, then:"
    echo "  make install-tools CONFIRM=yes"
    echo
    echo "Planned (idempotent, no sudo):"
    echo "  - On macOS with Homebrew: ensure leoafarias/fvm tap + install fvm if missing"
    echo "  - If mobile/.fvmrc (or fvm_config) pins a version: fvm install <version>"
    echo "  - Will NOT install Homebrew, JDK, Android Studio, Xcode, or run flutter upgrade"
    echo "  - Will NOT create a Flutter project or change the pinned version"
    return 1
  fi

  local os
  os="$(os_name)"
  if [[ "$os" == Darwin ]]; then
    if ! have brew; then
      print_fail "Homebrew missing."
      echo "Install Homebrew yourself: https://brew.sh"
      echo "To allow this Makefile to print-only guidance for brew install, do not set CONFIRM_BREW."
      echo "Refusing to install Homebrew automatically."
      return 1
    fi
    if ! have fvm; then
      echo "Installing FVM via Homebrew…"
      brew tap leoafarias/fvm
      brew install fvm
    else
      print_ok "fvm already installed"
    fi
  else
    if ! have fvm; then
      print_fail "Automatic FVM install is macOS/Homebrew-oriented."
      echo "Linux: follow https://fvm.app/documentation/getting-started/installation"
      echo "Then re-run make install-tools CONFIRM=yes"
      return 1
    fi
  fi

  if ! have fvm; then
    print_fail "fvm still not on PATH after install"
    return 1
  fi

  local ver
  if ! ver="$(require_pinned_version)"; then
    return 1
  fi

  echo "Installing Flutter SDK $ver via FVM (no upgrade of pin)…"
  mkdir -p "$MOBILE_DIR"
  (cd "$MOBILE_DIR" && fvm install "$ver")
  # Link project to pinned SDK without changing the version string
  if mobile_project_ready || [[ -f "$FVMRC" ]]; then
    (cd "$MOBILE_DIR" && fvm use "$ver" --force)
  fi
  print_ok "FVM Flutter $ver installed for mobile/"
}

setup_env() {
  # Prepare local env: require pin + fvm; pub get only if project exists
  if ! have fvm; then
    print_fail "fvm missing — run: make install-tools CONFIRM=yes"
    return 1
  fi
  local ver
  ver="$(require_pinned_version)" || return 1
  (cd "$MOBILE_DIR" && fvm install "$ver")
  (cd "$MOBILE_DIR" && fvm use "$ver" --force)
  if mobile_project_ready; then
    echo "Resolving Flutter deps (pub get; no upgrade)…"
    (cd "$MOBILE_DIR" && fvm flutter pub get)
  else
    print_warn "Skipping pub get — no pubspec.yaml yet (complete P11-T01 scaffold first)."
  fi
  print_ok "setup finished"
}

deps_get() {
  if ! have fvm; then
    print_fail "fvm required"
    return 1
  fi
  require_pinned_version >/dev/null
  run_in_mobile fvm flutter pub get
}

doctor() {
  if ! have fvm; then
    print_fail "fvm required for doctor"
    return 1
  fi
  if mobile_project_ready; then
    (cd "$MOBILE_DIR" && fvm flutter doctor -v)
  else
    print_warn "No pubspec — running fvm flutter doctor from mobile/ if SDK linked"
    mkdir -p "$MOBILE_DIR"
    (cd "$MOBILE_DIR" && fvm flutter doctor -v) || {
      print_fail "doctor failed — install pinned SDK first"
      return 1
    }
  fi
}

analyze_mobile() {
  run_in_mobile fvm flutter analyze
}

test_mobile() {
  run_in_mobile fvm flutter test
}

run_mobile() {
  local device="${1:-}"
  local api="${API_BASE_URL:-}"
  local args=()
  if [[ -n "$device" ]]; then
    args+=(-d "$device")
  fi
  if [[ -n "$api" ]]; then
    args+=(--dart-define="API_BASE_URL=$api")
  else
    print_warn "API_BASE_URL unset — app may need --dart-define=API_BASE_URL=... (see ADR-002)"
  fi
  if ! have fvm; then
    print_fail "fvm required"
    return 1
  fi
  if ! mobile_project_ready; then
    print_fail "No Flutter project"
    return 1
  fi
  (cd "$MOBILE_DIR" && fvm flutter run "${args[@]}")
}

clean_mobile() {
  if mobile_project_ready && have fvm; then
    (cd "$MOBILE_DIR" && fvm flutter clean)
    print_ok "Flutter clean done in mobile/"
  else
    print_warn "Skipping Flutter clean (no project or no fvm)"
  fi
}

# Dispatch when executed as: scripts/dev-flutter.sh <command> [args...]
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  cmd="${1:-}"
  shift || true
  case "$cmd" in
    check-tools) check_tools ;;
    check-env) check_env ;;
    status) status_report ;;
    install-tools) install_tools "${1:-}" ;;
    setup) setup_env ;;
    deps|get) deps_get ;;
    doctor) doctor ;;
    analyze) analyze_mobile ;;
    test) test_mobile ;;
    run) run_mobile "${1:-}" ;;
    clean-mobile) clean_mobile ;;
    pinned-version) require_pinned_version ;;
    *)
      echo "Usage: $0 {check-tools|check-env|status|install-tools|setup|deps|doctor|analyze|test|run|clean-mobile|pinned-version}"
      exit 2
      ;;
  esac
fi
