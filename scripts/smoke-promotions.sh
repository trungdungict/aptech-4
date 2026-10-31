#!/usr/bin/env bash
# Opt-in Docker/nginx smoke for the promotion journey (admin → public → checkout → order).
# Requires a running local stack (make deploy-local / up-all) and DEMO_ADMIN_* / DEMO_CUSTOMER_*.
# Creates only smoke-owned promotion rows (unique suffix) and cleans them up on exit.
# Does NOT wipe volumes, reset the demo DB, or use real payment gateways.
#
# Usage:
#   make smoke-promotions
#   # or:
#   bash ./scripts/smoke-promotions.sh
#
# Expiry job ACTIVE→EXPIRED status flip is covered by CampaignExpiryIntegrationTest (fixed Clock).
# This smoke validates window effectiveness without waiting on wall-clock expiry.
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

tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/aptech4-promo-smoke.XXXXXX")"
WORKDIR="$tmpdir"
trap 'rm -rf "$tmpdir"' EXIT

sfx="$(python3 -c 'import uuid; print(uuid.uuid4().hex[:8])')"
banner_title="Banner mùa vụ ${sfx}"
pct_name="Ưu đãi thành viên tạm ${sfx}"
banner_camp_name="Chiến dịch banner tạm ${sfx}"

# Owned resource IDs (filled as we go; cleaned in EXIT trap after trap replacement)
BANNER_STRAT=""
BANNER_CAMP=""
BANNER_PLACE=""
PCT_STRAT=""
PCT_CAMP=""
ADMIN_TOKEN=""
CUST_TOKEN=""

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
elif isinstance(cur, (dict, list)):
  print(json.dumps(cur))
else:
  print(cur)
' "$1" "$2"
}

http_json() {
	# usage: http_json OUTFILE METHOD URL [curl args...]
	local out="$1" method="$2" url="$3"
	shift 3
	local code
	code="$(curl -sS -o "$out" -w '%{http_code}' -X "$method" "$url" "$@")"
	echo "$code"
}

cleanup_owned() {
	set +e
	if [[ -n "${ADMIN_TOKEN}" ]]; then
		if [[ -n "${BANNER_PLACE}" ]]; then
			curl -sS -o /dev/null -X DELETE "$base/api/v1/admin/promotions/placements/${BANNER_PLACE}" \
				-H "Authorization: Bearer ${ADMIN_TOKEN}" || true
		fi
		if [[ -n "${BANNER_CAMP}" ]]; then
			curl -sS -o /dev/null -X POST "$base/api/v1/admin/promotions/campaigns/${BANNER_CAMP}/pause" \
				-H "Authorization: Bearer ${ADMIN_TOKEN}" || true
			curl -sS -o /dev/null -X DELETE "$base/api/v1/admin/promotions/campaigns/${BANNER_CAMP}" \
				-H "Authorization: Bearer ${ADMIN_TOKEN}" || true
		fi
		if [[ -n "${PCT_CAMP}" ]]; then
			curl -sS -o /dev/null -X POST "$base/api/v1/admin/promotions/campaigns/${PCT_CAMP}/pause" \
				-H "Authorization: Bearer ${ADMIN_TOKEN}" || true
			curl -sS -o /dev/null -X DELETE "$base/api/v1/admin/promotions/campaigns/${PCT_CAMP}" \
				-H "Authorization: Bearer ${ADMIN_TOKEN}" || true
		fi
		if [[ -n "${BANNER_STRAT}" ]]; then
			curl -sS -o /dev/null -X PATCH "$base/api/v1/admin/promotions/strategies/${BANNER_STRAT}/status" \
				-H "Authorization: Bearer ${ADMIN_TOKEN}" -H 'Content-Type: application/json' \
				-d '{"active":false}' || true
		fi
		if [[ -n "${PCT_STRAT}" ]]; then
			curl -sS -o /dev/null -X PATCH "$base/api/v1/admin/promotions/strategies/${PCT_STRAT}/status" \
				-H "Authorization: Bearer ${ADMIN_TOKEN}" -H 'Content-Type: application/json' \
				-d '{"active":false}' || true
		fi
	fi
	if [[ -n "${CUST_TOKEN}" ]]; then
		curl -sS -o /dev/null -X DELETE "$base/api/v1/cart/items" \
			-H "Authorization: Bearer ${CUST_TOKEN}" || true
	fi
	set -e
}

trap 'cleanup_owned; rm -rf "$tmpdir"' EXIT

pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

echo "smoke-promotions: health + web via nginx ($base) suffix=$sfx"

code="$(http_json "$WORKDIR/web.html" GET "$base/")"
[[ "$code" == "200" ]] || fail "web HTTP $code (is the stack up on WEB_PORT=$WEB_PORT?)"
pass "web UI HTTP 200"

code="$(http_json "$WORKDIR/health.json" GET "$base/api/v1/health")"
[[ "$code" == "200" ]] || fail "health HTTP $code"
grep -q '"status":"OK"' "$WORKDIR/health.json" || fail "unexpected health body"
pass "API health OK"

# --- Auth ---
code="$(http_json "$WORKDIR/admin-login.json" POST "$base/api/v1/auth/login" \
	-H 'Content-Type: application/json' \
	-d "{\"email\":\"$admin_email\",\"password\":\"$admin_pass\"}")"
[[ "$code" == "200" ]] || fail "admin login HTTP $code"
ADMIN_TOKEN="$(json_field "$WORKDIR/admin-login.json" accessToken)"
[[ -n "$ADMIN_TOKEN" ]] || fail "admin accessToken missing"
rm -f "$WORKDIR/admin-login.json"
pass "admin authenticate"

code="$(http_json "$WORKDIR/cust-login.json" POST "$base/api/v1/auth/login" \
	-H 'Content-Type: application/json' \
	-d "{\"email\":\"$cust_email\",\"password\":\"$cust_pass\"}")"
[[ "$code" == "200" ]] || fail "customer login HTTP $code"
CUST_TOKEN="$(json_field "$WORKDIR/cust-login.json" accessToken)"
[[ -n "$CUST_TOKEN" ]] || fail "customer accessToken missing"
rm -f "$WORKDIR/cust-login.json"
pass "customer authenticate"

code="$(http_json "$WORKDIR/cust-forbid.json" GET "$base/api/v1/admin/promotions/strategies" \
	-H "Authorization: Bearer $CUST_TOKEN")"
[[ "$code" == "403" ]] || fail "expected customer admin strategies 403, got $code"
pass "customer forbidden on admin promotion APIs"

# --- Prerequisites: FoodMart catalog + optional demo-promo seed ---
code="$(http_json "$WORKDIR/products-pre.json" GET "$base/api/v1/products?size=5&inStock=true")"
[[ "$code" == "200" ]] || fail "products prerequisite HTTP $code — is the stack API up?"
python3 - "$WORKDIR/products-pre.json" <<'PY' || fail "expected FOOD-* catalog (run: make seed-demo)"
import json,sys
d=json.load(open(sys.argv[1]))
items=d.get("items") or []
assert items, d
assert any(str(i.get("sku","")).startswith("FOOD-") for i in items), items
print("food_skus", [i.get("sku") for i in items[:3]])
PY
pass "prerequisite: FoodMart FOOD-* products present"

code="$(http_json "$WORKDIR/campaigns-pre.json" GET "$base/api/v1/admin/promotions/campaigns" \
	-H "Authorization: Bearer $ADMIN_TOKEN")"
[[ "$code" == "200" ]] || fail "admin campaign list HTTP $code"
python3 - "$WORKDIR/campaigns-pre.json" <<'PY'
import json,sys
rows=json.load(open(sys.argv[1]))
slugs={r.get("slug") for r in rows}
required={
  "banner-trang-chu","uu-dai-thanh-vien-10","uu-dai-don-hang-15k",
  "uu-dai-tao-tuoi-15","giam-8-do-uong","uu-dai-mix-or-5",
  "uu-dai-stack-pct-5","uu-dai-stack-fixed-5k","uu-dai-sap-dien-ra",
  "uu-dai-tam-dung","uu-dai-nhap","uu-dai-het-han",
}
missing=sorted(required - slugs)
if missing:
  print("WARN: demo promotion seed incomplete; missing:", ", ".join(missing))
  print("WARN: run `make seed-demo` for Phase 8 fixtures (smoke continues with ephemeral TMP-* rows)")
  open(sys.argv[1]+".missing","w").write("\n".join(missing))
else:
  print("demo_promo_slugs_ok", len(required))
  hero=next(r for r in rows if r.get("slug")=="banner-trang-chu")
  assert hero.get("status")=="ACTIVE", hero
  pct=next(r for r in rows if r.get("slug")=="uu-dai-thanh-vien-10")
  assert pct.get("status")=="PAUSED", pct
PY
if [[ -f "$WORKDIR/campaigns-pre.json.missing" ]]; then
	echo "INFO: continuing without full demo-promo seed"
else
	pass "prerequisite: grocery promo seed fixtures present"
	code="$(http_json "$WORKDIR/home-seed.json" GET "$base/api/v1/promotions/homepage")"
	[[ "$code" == "200" ]] || fail "homepage seed check HTTP $code"
	python3 - "$WORKDIR/home-seed.json" <<'PY' || fail "seeded hero missing from homepage"
import json,sys
d=json.load(open(sys.argv[1]))
hero=d.get("hero") or []
assert any(h.get("title")=="Ưu đãi mua sắm tiết kiệm" for h in hero), hero
assert any(
  (h.get("desktopImageUrl") or "").endswith("/bbbbbbbb-cccc-4ddd-8eee-ffffffffffff.jpg")
  for h in hero
), hero
print("seed_hero_ok")
PY
	pass "seeded banner-trang-chu visible on public homepage"
fi

# --- Invalid strategy rejected ---
code="$(http_json "$WORKDIR/bad-strat.json" POST "$base/api/v1/admin/promotions/strategies" \
	-H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"name\":\"Bad Banner $sfx\",\"type\":\"BANNER_ONLY\",\"discountPercent\":10}")"
[[ "$code" == "400" ]] || fail "expected invalid BANNER_ONLY+percent 400, got $code"
pass "invalid campaign/strategy data rejected (400)"

# --- Banner strategy + campaign + placement ---
code="$(http_json "$WORKDIR/banner-strat.json" POST "$base/api/v1/admin/promotions/strategies" \
	-H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"name\":\"Banner chiến dịch tạm $sfx\",\"code\":\"TMP-BAN-$sfx\",\"type\":\"BANNER_ONLY\",\"stackable\":false,\"priority\":10,\"active\":true}")"
[[ "$code" == "201" ]] || fail "banner strategy create HTTP $code"
BANNER_STRAT="$(json_field "$WORKDIR/banner-strat.json" id)"

# Tiny PNG upload purpose=PROMOTION
python3 - "$WORKDIR/ok.png" <<'PY'
import struct, zlib, sys
path = sys.argv[1]
def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)
raw = b"".join(b"\x00" + b"\x00\x00\x00" * 2 for _ in range(2))
ihdr = struct.pack(">IIBBBBB", 2, 2, 8, 2, 0, 0, 0)
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
open(path, "wb").write(png)
PY

code="$(http_json "$WORKDIR/upload.json" POST "$base/api/v1/admin/uploads/images?purpose=PROMOTION" \
	-H "Authorization: Bearer $ADMIN_TOKEN" \
	-F "file=@${WORKDIR}/ok.png;type=image/png")"
[[ "$code" == "200" ]] || fail "promotion image upload HTTP $code (body in $WORKDIR/upload.json)"
image_url="$(json_field "$WORKDIR/upload.json" imageUrl)"
[[ "$image_url" == /uploads/promotions/* ]] || fail "expected /uploads/promotions/… got $image_url"
pass "admin promotion image upload"

starts_at="$(python3 - <<'PY'
from datetime import datetime, timedelta, timezone
print((datetime.now(timezone.utc) - timedelta(hours=1)).strftime("%Y-%m-%dT%H:%M:%SZ"))
PY
)"
ends_at="$(python3 - <<'PY'
from datetime import datetime, timedelta, timezone
print((datetime.now(timezone.utc) + timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ"))
PY
)"

code="$(http_json "$WORKDIR/banner-camp.json" POST "$base/api/v1/admin/promotions/campaigns" \
	-H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"strategyId\":\"$BANNER_STRAT\",\"name\":\"$banner_camp_name\",\"slug\":\"tmp-banner-$sfx\",\"startsAt\":\"$starts_at\",\"endsAt\":\"$ends_at\"}")"
[[ "$code" == "201" ]] || fail "banner campaign create HTTP $code"
BANNER_CAMP="$(json_field "$WORKDIR/banner-camp.json" id)"
status="$(json_field "$WORKDIR/banner-camp.json" status)"
[[ "$status" == "DRAFT" ]] || fail "expected DRAFT campaign, got $status"
pass "admin create draft banner campaign"

code="$(http_json "$WORKDIR/banner-place.json" POST "$base/api/v1/admin/promotions/campaigns/${BANNER_CAMP}/placements" \
	-H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"placementKey\":\"HOME_HERO\",\"title\":\"$banner_title\",\"subtitle\":\"Ưu đãi theo mùa\",\"ctaLabel\":\"Shop\",\"ctaType\":\"NONE\",\"desktopImageUrl\":\"$image_url\",\"displayOrder\":1,\"enabled\":true}")"
[[ "$code" == "201" ]] || fail "placement create HTTP $code"
BANNER_PLACE="$(json_field "$WORKDIR/banner-place.json" id)"
pass "admin create HOME_HERO placement"

code="$(http_json "$WORKDIR/home-before.json" GET "$base/api/v1/promotions/homepage")"
[[ "$code" == "200" ]] || fail "homepage before activate HTTP $code"
python3 - "$WORKDIR/home-before.json" "$banner_title" <<'PY' || fail "draft campaign leaked into homepage"
import json,sys
d=json.load(open(sys.argv[1])); title=sys.argv[2]
hero=d.get("hero") or []
assert all(h.get("title") != title for h in hero), hero
print("hero_len", len(hero))
PY
pass "storefront hides draft / non-effective banner"

code="$(http_json "$WORKDIR/banner-act.json" POST "$base/api/v1/admin/promotions/campaigns/${BANNER_CAMP}/activate" \
	-H "Authorization: Bearer $ADMIN_TOKEN")"
[[ "$code" == "200" ]] || fail "activate banner HTTP $code"
[[ "$(json_field "$WORKDIR/banner-act.json" status)" == "ACTIVE" ]] || fail "activate did not set ACTIVE"

code="$(http_json "$WORKDIR/home-active.json" GET "$base/api/v1/promotions/homepage")"
[[ "$code" == "200" ]] || fail "homepage after activate HTTP $code"
python3 - "$WORKDIR/home-active.json" "$banner_title" <<'PY' || fail "active hero missing from homepage"
import json,sys
d=json.load(open(sys.argv[1])); title=sys.argv[2]
hero=d.get("hero") or []
assert any(h.get("title") == title for h in hero), hero
print("ok", title)
PY
pass "storefront displays eligible active hero"

# Persist evidence for optional UI follow-up (gitignored if present)
mkdir -p "$ROOT/frontend/.e2e-evidence"
printf '%s\n' "$banner_title" > "$ROOT/frontend/.e2e-evidence/promotions-hero-title.txt"
cp "$WORKDIR/home-active.json" "$ROOT/frontend/.e2e-evidence/promotions-homepage-active.json"

code="$(http_json "$WORKDIR/banner-pause.json" POST "$base/api/v1/admin/promotions/campaigns/${BANNER_CAMP}/pause" \
	-H "Authorization: Bearer $ADMIN_TOKEN")"
[[ "$code" == "200" ]] || fail "pause banner HTTP $code"
code="$(http_json "$WORKDIR/home-paused.json" GET "$base/api/v1/promotions/homepage")"
python3 - "$WORKDIR/home-paused.json" "$banner_title" <<'PY' || fail "paused campaign still on homepage"
import json,sys
d=json.load(open(sys.argv[1])); title=sys.argv[2]
hero=d.get("hero") or []
assert all(h.get("title") != title for h in hero), hero
PY
pass "storefront hides paused banner (fallback path remains for empty hero)"

# Re-activate for UI evidence window, then leave paused before pricing section
code="$(http_json "$WORKDIR/banner-react.json" POST "$base/api/v1/admin/promotions/campaigns/${BANNER_CAMP}/activate" \
	-H "Authorization: Bearer $ADMIN_TOKEN")"
[[ "$code" == "200" ]] || fail "re-activate banner HTTP $code"

# --- Percentage discount → checkout → order snapshot ---
code="$(http_json "$WORKDIR/pct-strat.json" POST "$base/api/v1/admin/promotions/strategies" \
	-H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"name\":\"Chiến lược giảm % tạm $sfx\",\"code\":\"TMP-PCT-$sfx\",\"type\":\"PERCENTAGE_DISCOUNT\",\"discountPercent\":10,\"stackable\":false,\"priority\":80,\"active\":true}")"
[[ "$code" == "201" ]] || fail "pct strategy create HTTP $code"
PCT_STRAT="$(json_field "$WORKDIR/pct-strat.json" id)"

code="$(http_json "$WORKDIR/pct-camp.json" POST "$base/api/v1/admin/promotions/campaigns" \
	-H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"strategyId\":\"$PCT_STRAT\",\"name\":\"$pct_name\",\"slug\":\"tmp-pct-$sfx\",\"startsAt\":\"$starts_at\",\"endsAt\":\"$ends_at\"}")"
[[ "$code" == "201" ]] || fail "pct campaign create HTTP $code"
PCT_CAMP="$(json_field "$WORKDIR/pct-camp.json" id)"

code="$(http_json "$WORKDIR/pct-act.json" POST "$base/api/v1/admin/promotions/campaigns/${PCT_CAMP}/activate" \
	-H "Authorization: Bearer $ADMIN_TOKEN")"
[[ "$code" == "200" ]] || fail "activate pct campaign HTTP $code"
pass "admin configure + activate percentage campaign"

# Clear cart, add in-stock product
curl -sS -o /dev/null -X DELETE "$base/api/v1/cart/items" -H "Authorization: Bearer $CUST_TOKEN"
code="$(http_json "$WORKDIR/product.json" GET "$base/api/v1/products?size=1&inStock=true")"
[[ "$code" == "200" ]] || fail "products HTTP $code"
product_id="$(python3 -c 'import json; print(json.load(open("'"$WORKDIR/product.json"'"))["items"][0]["id"])')"
unit_price="$(python3 -c 'import json; print(json.load(open("'"$WORKDIR/product.json"'"))["items"][0]["unitPrice"])')"

code="$(http_json "$WORKDIR/cart.json" POST "$base/api/v1/cart/items" \
	-H "Authorization: Bearer $CUST_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"productId\":\"$product_id\",\"quantity\":2}")"
[[ "$code" == "200" || "$code" == "201" ]] || fail "add cart HTTP $code"
subtotal="$(python3 -c 'import json; print(json.load(open("'"$WORKDIR/cart.json"'"))["subtotal"])')"

addr_id="$(curl -sS -H "Authorization: Bearer $CUST_TOKEN" "$base/api/v1/addresses" \
	| python3 -c 'import json,sys; a=json.load(sys.stdin); assert a, "no customer address"; print(a[0]["id"])')"

ikey="smoke-promo-checkout-$sfx"
code="$(http_json "$WORKDIR/checkout.json" POST "$base/api/v1/checkout" \
	-H "Authorization: Bearer $CUST_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"addressId\":\"$addr_id\",\"idempotencyKey\":\"$ikey\"}")"
[[ "$code" == "201" || "$code" == "200" ]] || fail "checkout HTTP $code (mock pay)"

python3 - "$WORKDIR/checkout.json" "$pct_name" <<'PY' || fail "checkout discount/snapshot assertions"
import json,sys,decimal
d=json.load(open(sys.argv[1])); name=sys.argv[2]
disc=decimal.Decimal(str(d["discountAmount"]))
sub=decimal.Decimal(str(d["subtotalAmount"]))
ship=decimal.Decimal(str(d.get("shippingAmount") or 0))
total=decimal.Decimal(str(d["totalAmount"]))
assert disc > 0, d
assert total == sub + ship - disc, (sub, ship, disc, total)
applied=d.get("appliedPromotions") or []
assert any(p.get("campaignName")==name for p in applied), applied
# Client must not send discount fields — response is server-owned
assert "preferCampaignIds" not in d
print("discount", disc, "total", total, "applied", len(applied))
PY
order_id="$(json_field "$WORKDIR/checkout.json" orderId)"
checkout_discount="$(json_field "$WORKDIR/checkout.json" discountAmount)"
pass "checkout applies backend discount + appliedPromotions (mock payment)"

code="$(http_json "$WORKDIR/order.json" GET "$base/api/v1/orders/${order_id}" \
	-H "Authorization: Bearer $CUST_TOKEN")"
[[ "$code" == "200" ]] || fail "order GET HTTP $code"
python3 - "$WORKDIR/order.json" "$pct_name" "$checkout_discount" <<'PY' || fail "order readback mismatch"
import json,sys,decimal
d=json.load(open(sys.argv[1])); name=sys.argv[2]; expected=decimal.Decimal(str(sys.argv[3]))
assert decimal.Decimal(str(d["discountAmount"])) == expected
applied=d.get("appliedPromotions") or []
assert any(p.get("campaignName")==name for p in applied), applied
print("order", d["id"], "discount", d["discountAmount"])
PY
pass "order detail shows persisted discount + promotion snapshot"

# Mutate live campaign after checkout — historical order must not change
code="$(http_json "$WORKDIR/pct-rename.json" PUT "$base/api/v1/admin/promotions/campaigns/${PCT_CAMP}" \
	-H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"strategyId\":\"$PCT_STRAT\",\"name\":\"Renamed After Checkout $sfx\",\"slug\":\"tmp-pct-$sfx\",\"startsAt\":\"$starts_at\",\"endsAt\":\"$ends_at\"}")"
[[ "$code" == "200" ]] || fail "campaign rename HTTP $code"

code="$(http_json "$WORKDIR/order-after.json" GET "$base/api/v1/orders/${order_id}" \
	-H "Authorization: Bearer $CUST_TOKEN")"
[[ "$code" == "200" ]] || fail "order GET after rename HTTP $code"
python3 - "$WORKDIR/order-after.json" "$pct_name" "$checkout_discount" <<'PY' || fail "historical snapshot mutated after campaign rename"
import json,sys,decimal
d=json.load(open(sys.argv[1])); name=sys.argv[2]; expected=decimal.Decimal(str(sys.argv[3]))
assert decimal.Decimal(str(d["discountAmount"])) == expected
applied=d.get("appliedPromotions") or []
assert any(p.get("campaignName")==name for p in applied), applied
assert not any(str(p.get("campaignName","")).startswith("Renamed After Checkout") for p in applied), applied
PY
pass "post-checkout campaign change does not alter historical order promotion details"

# --- Window effectiveness (no wall-clock wait; no job trigger) ---
# Shorten ACTIVE campaign endsAt into the past → public/pricing ignore it; status may still be ACTIVE until expiry job.
past_end="$(python3 - <<'PY'
from datetime import datetime, timedelta, timezone
print((datetime.now(timezone.utc) - timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%SZ"))
PY
)"
code="$(http_json "$WORKDIR/pct-expire-window.json" PUT "$base/api/v1/admin/promotions/campaigns/${PCT_CAMP}" \
	-H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"strategyId\":\"$PCT_STRAT\",\"name\":\"Renamed After Checkout $sfx\",\"slug\":\"tmp-pct-$sfx\",\"startsAt\":\"$starts_at\",\"endsAt\":\"$past_end\"}")"
[[ "$code" == "200" ]] || fail "set past endsAt HTTP $code"

code="$(http_json "$WORKDIR/banner-past.json" PUT "$base/api/v1/admin/promotions/campaigns/${BANNER_CAMP}" \
	-H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"strategyId\":\"$BANNER_STRAT\",\"name\":\"$banner_camp_name\",\"slug\":\"tmp-banner-$sfx\",\"startsAt\":\"$starts_at\",\"endsAt\":\"$past_end\"}")"
[[ "$code" == "200" ]] || fail "banner past window HTTP $code"

code="$(http_json "$WORKDIR/home-past.json" GET "$base/api/v1/promotions/homepage")"
python3 - "$WORKDIR/home-past.json" "$banner_title" <<'PY' || fail "past-window hero still public"
import json,sys
d=json.load(open(sys.argv[1])); title=sys.argv[2]
hero=d.get("hero") or []
assert all(h.get("title") != title for h in hero), hero
PY
pass "past endsAt window hides public placements (effectiveness; no sleep)"

# New cart should not receive discount from past-window campaign
curl -sS -o /dev/null -X DELETE "$base/api/v1/cart/items" -H "Authorization: Bearer $CUST_TOKEN"
code="$(http_json "$WORKDIR/cart2.json" POST "$base/api/v1/cart/items" \
	-H "Authorization: Bearer $CUST_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"productId\":\"$product_id\",\"quantity\":1}")"
ikey2="smoke-promo-past-$sfx"
code="$(http_json "$WORKDIR/checkout-past.json" POST "$base/api/v1/checkout" \
	-H "Authorization: Bearer $CUST_TOKEN" -H 'Content-Type: application/json' \
	-d "{\"addressId\":\"$addr_id\",\"idempotencyKey\":\"$ikey2\"}")"
[[ "$code" == "201" || "$code" == "200" ]] || fail "checkout past-window HTTP $code"
python3 - "$WORKDIR/checkout-past.json" <<'PY' || fail "past-window campaign still discounted checkout"
import json, sys, decimal
d=json.load(open(sys.argv[1]))
disc=decimal.Decimal(str(d.get("discountAmount") or 0))
assert disc == 0, d
assert not (d.get("appliedPromotions") or []), d.get("appliedPromotions")
print("ok zero discount")
PY
pass "past-window campaign does not discount new checkouts"

# Historical order still intact after window move
code="$(http_json "$WORKDIR/order-final.json" GET "$base/api/v1/orders/${order_id}" \
	-H "Authorization: Bearer $CUST_TOKEN")"
python3 - "$WORKDIR/order-final.json" "$checkout_discount" <<'PY' || fail "historical order changed after window move"
import json,sys,decimal
d=json.load(open(sys.argv[1])); expected=decimal.Decimal(str(sys.argv[2]))
assert decimal.Decimal(str(d["discountAmount"])) == expected
PY
pass "expiry/window change does not reprice historical orders"

echo
echo "OK: smoke-promotions (suffix=$sfx)"
echo "NOTE: ACTIVE→EXPIRED job transition is IT-covered (CampaignExpiryIntegrationTest); live stack has no test Clock hook."
