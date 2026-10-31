#!/usr/bin/env bash
# Offline checks for monitoring-validate-demo.sh (no Docker / live Prometheus required).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="$ROOT/scripts/monitoring-validate-demo.sh"
TMPDIR_TEST="$(mktemp -d)"
trap 'rm -rf "$TMPDIR_TEST"' EXIT

fail() { echo "ERROR: $*" >&2; exit 1; }

make_targets() {
	local out="$1"
	shift
	python3 - "$out" "$@" <<'PY'
import json, sys
path = sys.argv[1]
jobs = sys.argv[2:]
targets = []
for spec in jobs:
    job, health = spec.split("=", 1)
    targets.append({"labels": {"job": job}, "health": health})
json.dump({"data": {"activeTargets": targets}}, open(path, "w"))
PY
}

base_up=(
	"prometheus=up"
	"aptech4-api=up"
	"postgres-exporter=up"
	"redis-exporter=up"
)

echo "=== fixture: all required up + cadvisor up ==="
f="$TMPDIR_TEST/all-up.json"
make_targets "$f" "${base_up[@]}" "cadvisor=up"
MONITORING_VALIDATE_SKIP_HTTP=1 MONITORING_VALIDATE_TARGETS_JSON="$f" \
	bash "$SCRIPT" >/tmp/aptech4-mon-test-all.log 2>&1 \
	|| fail "expected success when all targets up"
grep -q "OK: monitoring-validate-demo" /tmp/aptech4-mon-test-all.log || fail "missing OK line"
! grep -q "^WARN: cAdvisor" /tmp/aptech4-mon-test-all.log || fail "unexpected cAdvisor warn"
echo "OK"

echo "=== fixture: required up, cadvisor down (warn only) ==="
f="$TMPDIR_TEST/cadvisor-down.json"
make_targets "$f" "${base_up[@]}" "cadvisor=down"
MONITORING_VALIDATE_SKIP_HTTP=1 MONITORING_VALIDATE_TARGETS_JSON="$f" \
	bash "$SCRIPT" >/tmp/aptech4-mon-test-cad.log 2>&1 \
	|| fail "cAdvisor down must not fail validation"
grep -q "WARN: cAdvisor" /tmp/aptech4-mon-test-cad.log || fail "expected cAdvisor WARN"
grep -q "OK: monitoring-validate-demo" /tmp/aptech4-mon-test-cad.log || fail "missing OK after warn"
echo "OK"

echo "=== fixture: prometheus down (must fail) ==="
f="$TMPDIR_TEST/prom-down.json"
make_targets "$f" "prometheus=down" "aptech4-api=up" "postgres-exporter=up" "redis-exporter=up" "cadvisor=up"
if MONITORING_VALIDATE_SKIP_HTTP=1 MONITORING_VALIDATE_TARGETS_JSON="$f" \
	bash "$SCRIPT" >/tmp/aptech4-mon-test-prom.log 2>&1; then
	fail "expected failure when prometheus target is down"
fi
grep -Eq "ERROR: required Prometheus targets" /tmp/aptech4-mon-test-prom.log || fail "expected ERROR for hard targets"
echo "OK"

echo "=== fixture: grafana skipped HTTP — hard target aptech4-api down ==="
f="$TMPDIR_TEST/api-down.json"
make_targets "$f" "prometheus=up" "aptech4-api=down" "postgres-exporter=up" "redis-exporter=up" "cadvisor=up"
if MONITORING_VALIDATE_SKIP_HTTP=1 MONITORING_VALIDATE_TARGETS_JSON="$f" \
	bash "$SCRIPT" >/tmp/aptech4-mon-test-api.log 2>&1; then
	fail "expected failure when aptech4-api target is down"
fi
echo "OK"

echo "OK: test-monitoring-validate-demo (hard fail + cAdvisor warn)"
