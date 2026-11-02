#!/usr/bin/env bash
# Opt-in Docker/nginx smoke for Admin Inventory + Analytics (P31).
# Requires a running local stack (make deploy-local / up-all) and DEMO_ADMIN_* / DEMO_CUSTOMER_*.
# Does NOT wipe volumes, reset the demo DB, or invent analytics rows.
#
# Usage:
#   make smoke-inventory-analytics
#   # or:
#   bash ./scripts/smoke-inventory-analytics.sh
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
admin_email="${DEMO_ADMIN_EMAIL:?DEMO_ADMIN_EMAIL required (set in .env or environment)}"
admin_pass="${DEMO_ADMIN_PASSWORD:?DEMO_ADMIN_PASSWORD required (set in .env or environment)}"
cust_email="${DEMO_CUSTOMER_EMAIL:?DEMO_CUSTOMER_EMAIL required (set in .env or environment)}"
cust_pass="${DEMO_CUSTOMER_PASSWORD:?DEMO_CUSTOMER_PASSWORD required (set in .env or environment)}"

tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/aptech4-inv-analytics-smoke.XXXXXX")"
trap 'rm -rf "$tmpdir"' EXIT

json_field() {
	python3 -c 'import json,sys
path=sys.argv[1]; key=sys.argv[2]
d=json.load(open(path))
cur=d
for part in key.split("."):
  if isinstance(cur, list):
    cur=cur[int(part)]
  else:
    cur=cur[part]
  if cur is None:
    print("")
    raise SystemExit(0)
if isinstance(cur, (dict, list)):
  print(json.dumps(cur))
else:
  print(cur)
' "$1" "$2"
}

http_json() {
	local out="$1" method="$2" url="$3"
	shift 3
	local code
	code="$(curl -sS -o "$out" -w '%{http_code}' -X "$method" "$url" "$@")"
	echo "$code"
}

echo "smoke-inventory-analytics: health + SPA routes via nginx ($base)"

code="$(curl -sS -o "$tmpdir/web.html" -w '%{http_code}' "$base/")"
[[ "$code" == "200" ]] || { echo "ERROR: web HTTP $code (stack up on WEB_PORT=$WEB_PORT?)"; exit 1; }

code="$(curl -sS -o "$tmpdir/health.json" -w '%{http_code}' "$base/api/v1/health")"
[[ "$code" == "200" ]] || { echo "ERROR: health HTTP $code"; exit 1; }
grep -q '"status":"OK"' "$tmpdir/health.json" || { echo "ERROR: unexpected health body"; exit 1; }

for path in /admin/inventory /admin/analytics; do
	code="$(curl -sS -o "$tmpdir/spa.html" -w '%{http_code}' "$base$path")"
	[[ "$code" == "200" ]] || { echo "ERROR: SPA deep link $path HTTP $code"; exit 1; }
	grep -qi '<!doctype html' "$tmpdir/spa.html" \
		|| { echo "ERROR: SPA shell missing for $path"; exit 1; }
	echo "PASS: SPA deep link $path → $code"
done

echo "smoke-inventory-analytics: admin + customer login"
code="$(http_json "$tmpdir/admin-login.json" POST "$base/api/v1/auth/login" \
	-H 'Content-Type: application/json' \
	-d "{\"email\":\"$admin_email\",\"password\":\"$admin_pass\"}")"
[[ "$code" == "200" ]] || { echo "ERROR: admin login HTTP $code"; exit 1; }
ADMIN_TOKEN="$(json_field "$tmpdir/admin-login.json" accessToken)"
rm -f "$tmpdir/admin-login.json"

code="$(http_json "$tmpdir/cust-login.json" POST "$base/api/v1/auth/login" \
	-H 'Content-Type: application/json' \
	-d "{\"email\":\"$cust_email\",\"password\":\"$cust_pass\"}")"
[[ "$code" == "200" ]] || { echo "ERROR: customer login HTTP $code"; exit 1; }
CUST_TOKEN="$(json_field "$tmpdir/cust-login.json" accessToken)"
rm -f "$tmpdir/cust-login.json"

echo "smoke-inventory-analytics: customer/anonymous must be forbidden"
for path in \
	/api/v1/admin/inventory/summary \
	/api/v1/admin/analytics/overview
do
	code="$(http_json "$tmpdir/forbid.json" GET "$base$path" \
		-H "Authorization: Bearer ${CUST_TOKEN}")"
	[[ "$code" == "403" ]] || { echo "ERROR: expected 403 for customer $path, got $code"; exit 1; }
	echo "PASS: customer forbidden $path"
done

anon_code="$(http_json "$tmpdir/anon.json" GET "$base/api/v1/admin/inventory/summary")"
[[ "$anon_code" == "401" || "$anon_code" == "403" ]] \
	|| { echo "ERROR: expected anonymous inventory summary 401/403, got $anon_code"; exit 1; }
echo "PASS: anonymous inventory summary rejected ($anon_code)"

echo "smoke-inventory-analytics: inventory summary + list filters"
code="$(http_json "$tmpdir/inv-summary.json" GET "$base/api/v1/admin/inventory/summary" \
	-H "Authorization: Bearer ${ADMIN_TOKEN}")"
[[ "$code" == "200" ]] || { echo "ERROR: inventory summary HTTP $code"; exit 1; }
python3 - "$tmpdir/inv-summary.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
for k in ("totalProducts", "inStockCount", "lowStockCount", "outOfStockCount", "lowStockThreshold", "availableDefinition"):
    assert k in d, k
assert d["totalProducts"] >= 0
assert d["lowStockThreshold"] >= 0
print("PASS: inventory summary fields ok threshold=", d["lowStockThreshold"], "total=", d["totalProducts"])
PY

code="$(http_json "$tmpdir/inv-list.json" GET \
	"$base/api/v1/admin/inventory?page=0&size=5&sort=name,asc&stockStatus=LOW" \
	-H "Authorization: Bearer ${ADMIN_TOKEN}")"
[[ "$code" == "200" ]] || { echo "ERROR: inventory list HTTP $code"; exit 1; }
python3 - "$tmpdir/inv-list.json" "$tmpdir/inv-pick.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
assert "items" in d and "totalElements" in d and "page" in d
items = d.get("items") or []
assert items, "inventory list empty — seed demo first (make seed-demo)"
pick = next((it for it in items if it.get("hasInventoryRow")), items[0])
json.dump(pick, open(sys.argv[2], "w"))
print("PASS: inventory list page size=", len(items), "totalElements=", d["totalElements"])
print("INFO: pick productId=", pick["productId"], "onHand=", pick["quantityOnHand"], "reserved=", pick["quantityReserved"])
PY

code="$(http_json "$tmpdir/inv-search.json" GET \
	"$base/api/v1/admin/inventory?q=FOOD&page=0&size=5" \
	-H "Authorization: Bearer ${ADMIN_TOKEN}")"
[[ "$code" == "200" ]] || { echo "ERROR: inventory search HTTP $code"; exit 1; }
echo "PASS: inventory search q=FOOD"

PRODUCT_ID="$(json_field "$tmpdir/inv-pick.json" productId)"
ON_HAND="$(json_field "$tmpdir/inv-pick.json" quantityOnHand)"
RESERVED="$(json_field "$tmpdir/inv-pick.json" quantityReserved)"

echo "smoke-inventory-analytics: absolute PUT + reject invalid on-hand"
code="$(http_json "$tmpdir/inv-put-ok.json" PUT \
	"$base/api/v1/admin/products/${PRODUCT_ID}/inventory" \
	-H "Authorization: Bearer ${ADMIN_TOKEN}" \
	-H 'Content-Type: application/json' \
	-d "{\"quantityOnHand\": ${ON_HAND}}")"
[[ "$code" == "200" ]] || { echo "ERROR: inventory PUT ok HTTP $code"; cat "$tmpdir/inv-put-ok.json" >&2; exit 1; }
python3 - "$tmpdir/inv-put-ok.json" "$RESERVED" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
assert "quantityOnHand" in d and "quantityReserved" in d and "available" in d
assert int(d["quantityReserved"]) == int(sys.argv[2])
print("PASS: absolute PUT ok; reserved unchanged=", d["quantityReserved"])
PY

code="$(http_json "$tmpdir/inv-put-neg.json" PUT \
	"$base/api/v1/admin/products/${PRODUCT_ID}/inventory" \
	-H "Authorization: Bearer ${ADMIN_TOKEN}" \
	-H 'Content-Type: application/json' \
	-d '{"quantityOnHand": -1}')"
[[ "$code" == "400" ]] || { echo "ERROR: expected negative PUT 400, got $code"; exit 1; }
echo "PASS: negative on-hand rejected"

if [[ "${RESERVED}" =~ ^[0-9]+$ && "${RESERVED}" -gt 0 ]]; then
	below=$((RESERVED - 1))
	code="$(http_json "$tmpdir/inv-put-below.json" PUT \
		"$base/api/v1/admin/products/${PRODUCT_ID}/inventory" \
		-H "Authorization: Bearer ${ADMIN_TOKEN}" \
		-H 'Content-Type: application/json' \
		-d "{\"quantityOnHand\": ${below}}")"
	[[ "$code" == "400" ]] || { echo "ERROR: expected below-reserved PUT 400, got $code"; exit 1; }
	echo "PASS: below-reserved on-hand rejected (reserved=$RESERVED)"
else
	echo "WARN: reserved=0 on smoke product; below-reserved PUT skipped (covered by ITs)"
fi

code="$(http_json "$tmpdir/inv-summary2.json" GET "$base/api/v1/admin/inventory/summary" \
	-H "Authorization: Bearer ${ADMIN_TOKEN}")"
[[ "$code" == "200" ]] || { echo "ERROR: inventory summary after PUT HTTP $code"; exit 1; }
echo "PASS: inventory summary refresh after PUT"

echo "smoke-inventory-analytics: six analytics endpoints + date window"
code="$(http_json "$tmpdir/a-overview.json" GET "$base/api/v1/admin/analytics/overview" \
	-H "Authorization: Bearer ${ADMIN_TOKEN}")"
[[ "$code" == "200" ]] || { echo "ERROR: analytics overview HTTP $code"; cat "$tmpdir/a-overview.json" >&2; exit 1; }
python3 - "$tmpdir/a-overview.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
for k in (
    "from", "to", "timezone", "currency", "revenueTotal", "averageOrderValue", "cancellationRate",
    "placedOrderCount", "revenueOrderCount", "attention", "inventory", "empty", "revenueDefinition",
):
    assert k in d, k
assert d["timezone"] == "UTC"
inv = d["inventory"]
for k in ("totalProducts", "inStockCount", "lowStockCount", "outOfStockCount", "lowStockThreshold"):
    assert k in inv, k
att = d["attention"]
assert "paid" in att and "processing" in att and "totalMatching" in att
print("PASS: overview KPIs + embedded inventory; empty=", d["empty"], "window=", d["from"], "→", d["to"])
PY

FROM_EXPLICIT="2026-01-01T00:00:00.000Z"
TO_EXPLICIT="2026-01-08T00:00:00.000Z"

code="$(http_json "$tmpdir/a-overview-ex.json" GET \
	"$base/api/v1/admin/analytics/overview?from=${FROM_EXPLICIT}&to=${TO_EXPLICIT}" \
	-H "Authorization: Bearer ${ADMIN_TOKEN}")"
[[ "$code" == "200" ]] || { echo "ERROR: overview explicit range HTTP $code"; exit 1; }
python3 - "$tmpdir/a-overview-ex.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
assert d["from"].startswith("2026-01-01T00:00:00")
assert d["to"].startswith("2026-01-08T00:00:00")
print("PASS: explicit Instant window from=", d["from"], "to=", d["to"])
PY

code="$(http_json "$tmpdir/a-bad-range.json" GET \
	"$base/api/v1/admin/analytics/overview?from=${TO_EXPLICIT}&to=${FROM_EXPLICIT}" \
	-H "Authorization: Bearer ${ADMIN_TOKEN}")"
[[ "$code" == "400" ]] || { echo "ERROR: expected inverted range 400, got $code"; exit 1; }
echo "PASS: inverted analytics range rejected"

check_analytics() {
	local label="$1" url="$2" keys_csv="$3"
	local out="$tmpdir/a-${label}.json"
	local code
	code="$(http_json "$out" GET "$url" -H "Authorization: Bearer ${ADMIN_TOKEN}")"
	[[ "$code" == "200" ]] || { echo "ERROR: analytics $label HTTP $code"; cat "$out" >&2; exit 1; }
	python3 - "$out" "$keys_csv" "$label" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
keys = sys.argv[2].split(",")
label = sys.argv[3]
for k in keys:
    assert k in d, f"{label} missing {k}"
if "points" in d:
    assert isinstance(d["points"], list)
    for p in d["points"]:
        assert "date" in p and "placedOrderCount" in p and "revenueTotal" in p
if "counts" in d:
    for st in ("PENDING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "COMPLETED", "CANCELLED", "FAILED"):
        assert st in d["counts"], st
if "attributionNote" in d:
    note = d["attributionNote"].lower()
    assert "category" in note or "live" in note
print("PASS: analytics", label)
PY
}

check_analytics status \
	"$base/api/v1/admin/analytics/orders/status?from=${FROM_EXPLICIT}&to=${TO_EXPLICIT}" \
	"counts,total,empty"

check_analytics timeseries \
	"$base/api/v1/admin/analytics/orders/timeseries?from=${FROM_EXPLICIT}&to=${TO_EXPLICIT}" \
	"bucket,points,empty"

check_analytics top \
	"$base/api/v1/admin/analytics/products/top?from=${FROM_EXPLICIT}&to=${TO_EXPLICIT}&limit=5" \
	"items,empty,note"

check_analytics categories \
	"$base/api/v1/admin/analytics/categories/sales?from=${FROM_EXPLICIT}&to=${TO_EXPLICIT}&limit=5" \
	"items,empty,attributionNote"

check_analytics promotions \
	"$base/api/v1/admin/analytics/promotions/summary?from=${FROM_EXPLICIT}&to=${TO_EXPLICIT}" \
	"discountTotal,ordersWithPromotionCount,breakdown,empty"

code="$(http_json "$tmpdir/a-bad-limit.json" GET \
	"$base/api/v1/admin/analytics/products/top?from=${FROM_EXPLICIT}&to=${TO_EXPLICIT}&limit=21" \
	-H "Authorization: Bearer ${ADMIN_TOKEN}")"
[[ "$code" == "400" ]] || { echo "ERROR: expected limit=21 → 400, got $code"; exit 1; }
echo "PASS: top products limit>20 rejected"

python3 - "$tmpdir/a-timeseries.json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
assert d.get("bucket") == "DAY"
# exclusive 7-day Instant window → 7 UTC day buckets
assert len(d["points"]) == 7, len(d["points"])
print("PASS: timeseries zero-filled day count=", len(d["points"]))
PY

echo "OK: smoke-inventory-analytics complete"
