#!/usr/bin/env bash
# Offline regression for deploy/nginx/local-stack.conf:
# hashed /assets/* must 404 when missing — never SPA-fallback to index.html.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
conf="$ROOT/deploy/nginx/local-stack.conf"

grep -q 'location /assets/' "$conf" || {
	echo "ERROR: missing location /assets/ in $conf" >&2
	exit 1
}
grep -A6 'location /assets/' "$conf" | grep -q 'try_files \$uri =404' || {
	echo "ERROR: /assets/ must use try_files \$uri =404 (no index.html fallback)" >&2
	exit 1
}
grep -q 'location = /index.html' "$conf" || {
	echo "ERROR: missing explicit index.html cache policy" >&2
	exit 1
}
grep -A4 'location = /index.html' "$conf" | grep -qi 'no-cache' || {
	echo "ERROR: index.html should send Cache-Control no-cache" >&2
	exit 1
}
echo "OK: nginx assets config rejects SPA fallback for /assets/"
