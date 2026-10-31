#!/usr/bin/env bash
# Require Prometheus + Grafana healthy and critical scrape targets up (complete demo stack).
# cAdvisor is advisory on Docker Desktop (Mac/WSL2): warn if down, do not fail the demo stack.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ -f .env ]]; then
	set -a
	# shellcheck disable=SC1091
	source ./.env
	set +a
fi

PROM="http://127.0.0.1:${PROMETHEUS_PORT:-9090}"
GRAF="http://127.0.0.1:${GRAFANA_PORT:-3001}"
# Optional fixtures for offline tests (skip live HTTP to Prometheus/Grafana).
TARGETS_JSON="${MONITORING_VALIDATE_TARGETS_JSON:-/tmp/aptech4-prom-targets.json}"
SKIP_HTTP="${MONITORING_VALIDATE_SKIP_HTTP:-0}"

REQUIRED_HARD=("prometheus" "aptech4-api" "postgres-exporter" "redis-exporter")
OPTIONAL_WARN=("cadvisor")

check_targets_file() {
	local attempt="${1:-1}"
	python3 - "$TARGETS_JSON" "$attempt" <<'PY'
import json, sys

path, attempt = sys.argv[1], sys.argv[2]
required = {"prometheus", "aptech4-api", "postgres-exporter", "redis-exporter"}
optional = {"cadvisor"}

d = json.load(open(path))
health = {}
for t in d.get("data", {}).get("activeTargets", []):
    job = t.get("labels", {}).get("job")
    health[job] = t.get("health")
print("targets", sorted(health.items()), "attempt", attempt)

missing_hard = [j for j in sorted(required) if health.get(j) != "up"]
if missing_hard:
    raise SystemExit("waiting for targets: " + ",".join(missing_hard))

missing_opt = [j for j in sorted(optional) if health.get(j) != "up"]
if missing_opt:
    # Non-zero special code so caller can warn without failing hard targets.
    print("OPTIONAL_DOWN=" + ",".join(missing_opt))
    raise SystemExit(2)
PY
}

if [[ "$SKIP_HTTP" != "1" ]]; then
	echo "Validating monitoring for complete demo stack…"
	curl -fsS "$PROM/-/ready" >/dev/null \
		|| { echo "ERROR: Prometheus not ready at $PROM/-/ready" >&2; exit 1; }
	curl -fsS "$GRAF/api/health" | python3 -c 'import sys,json; h=json.load(sys.stdin); assert h.get("database")=="ok", h; print("OK: grafana", h)' \
		|| { echo "ERROR: Grafana unhealthy at $GRAF/api/health" >&2; exit 1; }
fi

ok=0
optional_warn=0
for i in $(seq 1 24); do
	if [[ "$SKIP_HTTP" != "1" ]]; then
		curl -fsS "$PROM/api/v1/targets" -o "$TARGETS_JSON"
	fi
	set +e
	check_targets_file "$i"
	rc=$?
	set -e
	if [[ "$rc" -eq 0 ]]; then
		ok=1
		optional_warn=0
		break
	fi
	if [[ "$rc" -eq 2 ]]; then
		# Hard targets up; optional (cAdvisor) still down — accept after first success of hard set.
		ok=1
		optional_warn=1
		break
	fi
	if [[ "$SKIP_HTTP" == "1" ]]; then
		# Fixture mode: single attempt
		break
	fi
	sleep 5
done

if [[ "$ok" -ne 1 ]]; then
	echo "ERROR: required Prometheus targets not all up (need: ${REQUIRED_HARD[*]})" >&2
	head -c 2000 "$TARGETS_JSON" 2>/dev/null || true
	echo >&2
	exit 1
fi

if [[ "$optional_warn" -eq 1 ]]; then
	echo "WARN: cAdvisor Prometheus target is not up (common on Docker Desktop Mac/WSL2)." >&2
	echo "WARN: Core monitoring (Prometheus, Grafana, api/postgres/redis exporters) is OK; continuing." >&2
	echo "OK: monitoring-validate-demo (Prometheus ready, Grafana ok, required targets up; cAdvisor warned)"
else
	echo "OK: monitoring-validate-demo (Prometheus ready, Grafana ok, required targets up)"
fi
