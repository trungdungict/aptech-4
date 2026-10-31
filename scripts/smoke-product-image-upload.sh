#!/usr/bin/env bash
# Opt-in Docker/nginx smoke for Admin product image upload.
# Requires a running local stack (make deploy-local / up-all) and DEMO_ADMIN_* credentials.
# Does NOT wipe volumes, reset the demo DB, or delete unrelated uploads.
#
# Usage:
#   make smoke-product-image-upload
#   # or:
#   bash ./scripts/smoke-product-image-upload.sh
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
email="${DEMO_ADMIN_EMAIL:?DEMO_ADMIN_EMAIL required (set in .env or environment)}"
pass="${DEMO_ADMIN_PASSWORD:?DEMO_ADMIN_PASSWORD required (set in .env or environment)}"
max_bytes="${PRODUCT_IMAGE_MAX_SIZE_BYTES:-2097152}"

tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/aptech4-img-smoke.XXXXXX")"
trap 'rm -rf "$tmpdir"' EXIT

echo "smoke-product-image-upload: health + admin login via nginx ($base)"

code="$(curl -sS -o /tmp/aptech4-img-web.html -w '%{http_code}' "$base/")"
if [[ "$code" != "200" ]]; then
	echo "ERROR: web HTTP $code (is the stack up on WEB_PORT=$WEB_PORT?)" >&2
	exit 1
fi

code="$(curl -sS -o /tmp/aptech4-img-health.json -w '%{http_code}' "$base/api/v1/health")"
if [[ "$code" != "200" ]]; then
	echo "ERROR: health HTTP $code" >&2
	exit 1
fi
grep -q '"status":"OK"' /tmp/aptech4-img-health.json || {
	echo "ERROR: unexpected health body" >&2
	cat /tmp/aptech4-img-health.json >&2 || true
	exit 1
}

code="$(curl -sS -o /tmp/aptech4-img-login.json -w '%{http_code}' \
	-X POST "$base/api/v1/auth/login" \
	-H 'Content-Type: application/json' \
	-d "{\"email\":\"$email\",\"password\":\"$pass\"}")"
if [[ "$code" != "200" ]]; then
	echo "ERROR: admin login HTTP $code" >&2
	# Do not dump body (may contain tokens); status only.
	exit 1
fi
token="$(python3 -c 'import json; print(json.load(open("/tmp/aptech4-img-login.json"))["accessToken"])')"
# Avoid leaving access token on disk longer than needed
rm -f /tmp/aptech4-img-login.json

# Generate a tiny valid PNG without leaking credentials into logs
python3 - "$tmpdir/ok.png" <<'PY'
import struct, zlib, sys
path = sys.argv[1]
def chunk(tag, data):
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)
raw = b"".join(b"\x00" + b"\x00\x00\x00" * 2 for _ in range(2))
ihdr = struct.pack(">IIBBBBB", 2, 2, 8, 2, 0, 0, 0)
png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
open(path, "wb").write(png)
print("wrote", path, "bytes", len(png))
PY

echo "smoke-product-image-upload: anonymous upload must fail"
anon_code="$(curl -sS -o /tmp/aptech4-img-anon.json -w '%{http_code}' \
	-X POST "$base/api/v1/admin/uploads/images" \
	-F "file=@${tmpdir}/ok.png;type=image/png")"
if [[ "$anon_code" != "401" && "$anon_code" != "403" ]]; then
	echo "ERROR: expected anonymous upload 401/403, got $anon_code" >&2
	exit 1
fi

echo "smoke-product-image-upload: exact-limit payload must fail"
python3 - "$tmpdir/exact.bin" "$max_bytes" <<'PY'
import sys
size = int(sys.argv[2])
# PNG signature prefix so content-type looks plausible; size == max must be rejected
data = bytearray(size)
data[0:8] = b"\x89PNG\r\n\x1a\n"
open(sys.argv[1], "wb").write(data)
PY
exact_code="$(curl -sS -o /tmp/aptech4-img-exact.json -w '%{http_code}' \
	-X POST "$base/api/v1/admin/uploads/images" \
	-H "Authorization: Bearer $token" \
	-F "file=@${tmpdir}/exact.bin;type=image/png;filename=exact.png")"
# App-level validation returns 400; nginx/Spring multipart may reject first with 413.
if [[ "$exact_code" != "400" && "$exact_code" != "413" ]]; then
	echo "ERROR: expected exact-limit upload HTTP 400/413, got $exact_code" >&2
	exit 1
fi
echo "exact-limit rejected with HTTP $exact_code"

echo "smoke-product-image-upload: admin upload valid PNG"
code="$(curl -sS -o /tmp/aptech4-img-upload.json -w '%{http_code}' \
	-X POST "$base/api/v1/admin/uploads/images" \
	-H "Authorization: Bearer $token" \
	-F "file=@${tmpdir}/ok.png;type=image/png")"
if [[ "$code" != "200" ]]; then
	echo "ERROR: upload HTTP $code" >&2
	python3 -c 'import json; d=json.load(open("/tmp/aptech4-img-upload.json")); print(d.get("message") or d.get("error") or "no message")' 2>/dev/null || true
	if [[ "$code" == "404" ]]; then
		echo "HINT: authenticated 404 usually means the running api image predates AdminProductImageUploadController." >&2
		echo "      Rebuild/redeploy the stack API (e.g. make build-stack && make deploy-local) then re-run this smoke." >&2
	fi
	exit 1
fi

image_url="$(python3 - <<'PY'
import json
d = json.load(open("/tmp/aptech4-img-upload.json"))
for key in ("imageUrl", "contentType", "sizeBytes"):
    if key not in d:
        raise SystemExit(f"missing {key}")
url = d["imageUrl"]
if not url.startswith("/uploads/products/") or ".." in url or url.startswith("file:") or "\\" in url:
    raise SystemExit(f"invalid imageUrl: {url!r}")
print(url)
PY
)"
echo "uploaded path: $image_url"

echo "smoke-product-image-upload: fetch via nginx public route"
code="$(curl -sS -o "$tmpdir/fetched.bin" -w '%{http_code}' -D "$tmpdir/headers.txt" "$base$image_url")"
if [[ "$code" != "200" ]]; then
	echo "ERROR: GET $image_url HTTP $code" >&2
	exit 1
fi
python3 - "$tmpdir/fetched.bin" "$tmpdir/headers.txt" <<'PY'
import sys
data = open(sys.argv[1], "rb").read()
if data[:8] != b"\x89PNG\r\n\x1a\n":
    raise SystemExit("fetched bytes are not a PNG signature")
headers = open(sys.argv[2], "r", errors="replace").read().lower()
if "x-content-type-options: nosniff" not in headers:
    raise SystemExit("missing X-Content-Type-Options: nosniff on upload response")
if "content-type:" in headers and "image/png" not in headers and "image/" not in headers:
    raise SystemExit("unexpected content-type for uploaded image")
print("fetched", len(data), "bytes OK")
PY

echo "smoke-product-image-upload: cleanup unreferenced upload artifact via API container"
filename="${image_url##*/}"
# Only delete the file created by this smoke when it is not referenced by any product.
# Uses the Compose API container filesystem (volume-backed); never drops DBs/volumes.
if docker ps --format '{{.Names}}' | grep -qx 'aptech4-api'; then
	ref_count="$(docker exec aptech4-postgres psql -U aptech -d aptech4 -Atc \
		"SELECT COUNT(*) FROM products WHERE image_url = '${image_url}';" 2>/dev/null || echo "skip")"
	if [[ "$ref_count" == "0" ]]; then
		docker exec aptech4-api sh -c "rm -f /app/uploads/products/${filename}" || true
		echo "cleaned unreferenced test file $filename"
	elif [[ "$ref_count" == "skip" ]]; then
		echo "NOTE: could not query products; leaving $filename on volume"
	else
		echo "NOTE: image referenced by $ref_count product(s); leaving file in place"
	fi
else
	echo "NOTE: aptech4-api container not found; leaving $filename (manual cleanup if needed)"
fi

rm -f /tmp/aptech4-img-upload.json /tmp/aptech4-img-anon.json /tmp/aptech4-img-exact.json /tmp/aptech4-img-health.json
echo "OK: smoke-product-image-upload"
