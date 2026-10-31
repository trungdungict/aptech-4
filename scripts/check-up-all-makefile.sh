#!/usr/bin/env bash
# Static check: make up-all must wire the complete local demo stack (no Docker required).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAKEFILE="$ROOT/Makefile"

fail() { echo "ERROR: $*" >&2; exit 1; }

recipe="$(awk '/^up-all:/{print; exit}' "$MAKEFILE")"
[[ -n "$recipe" ]] || fail "up-all target missing"

for need in deploy-demo-stack ollama-ensure-model seed-demo smoke-demo smoke-chat monitoring-validate-demo; do
	echo "$recipe" | grep -q "$need" || fail "up-all must depend on $need (got: $recipe)"
done

grep -q '^deploy-demo-stack:' "$MAKEFILE" || fail "deploy-demo-stack target missing"
grep -q 'profile stack --profile ai --profile monitoring' "$MAKEFILE" || fail "deploy-demo-stack must enable stack+ai+monitoring profiles"
grep -q 'APTECH_AI_ENABLED=true' "$MAKEFILE" || fail "AI enable flag missing from Makefile"
grep -q '^smoke-chat:' "$MAKEFILE" || fail "smoke-chat target missing"
grep -q '^monitoring-validate-demo:' "$MAKEFILE" || fail "monitoring-validate-demo target missing"
grep -q 'smoke-chat.sh' "$MAKEFILE" || fail "smoke-chat must invoke scripts/smoke-chat.sh"
grep -q 'monitoring-validate-demo.sh' "$MAKEFILE" || fail "monitoring-validate-demo must invoke its script"
grep -q '/api/v1/chat/health' "$ROOT/scripts/smoke-chat.sh" || fail "smoke-chat.sh must probe /api/v1/chat/health"
grep -q 'aptech4-api' "$ROOT/scripts/monitoring-validate-demo.sh" || fail "monitoring-validate-demo.sh must require aptech4-api"
grep -q 'OPTIONAL_WARN' "$ROOT/scripts/monitoring-validate-demo.sh" || fail "cAdvisor must be optional/warn in monitoring-validate-demo.sh"
grep -q 'WARN: cAdvisor' "$ROOT/scripts/monitoring-validate-demo.sh" || fail "missing cAdvisor WARN message"

# Homebrew JDK must be gated (WSL/Linux must not force macOS paths)
grep -q 'HOMEBREW_OPENJDK21_BIN' "$MAKEFILE" || fail "Makefile missing HOMEBREW_OPENJDK21_BIN"
grep -q 'test -x \$(HOMEBREW_OPENJDK21_BIN)/java' "$MAKEFILE" || fail "Makefile must guard Homebrew OpenJDK with test -x"
if grep -E '^export PATH := /opt/homebrew/opt/openjdk@21/bin:' "$MAKEFILE"; then
	fail "Makefile must not unconditionally prepend Homebrew OpenJDK to PATH"
fi

# down-all must still wipe all profiles + volumes with CONFIRM gate
grep -A20 '^down-all:' "$MAKEFILE" | grep -q 'CONFIRM' || fail "down-all must require CONFIRM"
grep -A20 '^down-all:' "$MAKEFILE" | grep -q -- '--volumes' || fail "down-all must remove volumes"
# ordinary down must not pass --volumes
if awk '/^down:/{p=1;next} p&&/^[^[:space:]#]/{exit} p' "$MAKEFILE" | grep -q -- '--volumes'; then
	fail "ordinary down must not remove volumes (--volumes)"
fi

# Docs: WSL2 guidance present
grep -q 'Windows 11 + WSL2' "$ROOT/docs/DEMO_ENVIRONMENT.md" || fail "DEMO_ENVIRONMENT.md missing WSL2 section"
grep -q 'not.*PowerShell' "$ROOT/docs/DEMO_ENVIRONMENT.md" || fail "DEMO_ENVIRONMENT.md must say not PowerShell/CMD"
grep -q '/mnt/c' "$ROOT/docs/DEMO_ENVIRONMENT.md" || fail "DEMO_ENVIRONMENT.md must warn against /mnt/c"
grep -q 'JDK 21' "$ROOT/docs/DEMO_ENVIRONMENT.md" || fail "DEMO_ENVIRONMENT.md must require host JDK 21 for seed"

echo "OK: up-all Makefile wiring includes core + AI + monitoring + seed + smokes"
