#!/usr/bin/env python3
"""Validate Postman collection/environment JSON for Aptech-4 local stack."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COLLECTION = ROOT / "postman" / "APTECH-4-Local.postman_collection.json"
ENVIRONMENT = ROOT / "postman" / "APTECH-4-Local.postman_environment.json"

REQUIRED_REQUEST_NAMES = {
	"Health",
	"List categories",
	"List products",
	"Search/filter products",
	"Get product by id",
	"Register customer",
	"Login customer",
	"Login admin",
	"Refresh tokens",
	"Logout (destructive session)",
	"Get cart",
	"Add cart item",
	"Update cart item qty",
	"Remove cart item",
	"Create address",
	"Checkout (mock payment)",
	"List my orders",
	"Get my order",
	"Cancel my order (PENDING_PAYMENT only)",
	"Admin list products",
	"Admin create product",
	"Admin update product",
	"Admin product status (soft-disable)",
	"Admin list categories",
	"Admin create category",
	"Admin update category",
	"Admin category status (soft-disable)",
	"Admin list orders",
	"Admin get order",
	"Admin update order status",
}


def walk_names(items: list, found: set[str]) -> None:
	for it in items:
		if "item" in it:
			walk_names(it["item"], found)
		else:
			found.add(it["name"])


def main() -> int:
	errors: list[str] = []
	for path in (COLLECTION, ENVIRONMENT):
		if not path.is_file():
			errors.append(f"missing file: {path}")
			continue
		try:
			data = json.loads(path.read_text(encoding="utf-8"))
		except json.JSONDecodeError as exc:
			errors.append(f"invalid JSON {path}: {exc}")
			continue
		if path == COLLECTION:
			if data.get("info", {}).get("schema") != (
				"https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
			):
				errors.append("collection schema is not Postman Collection v2.1")
			found: set[str] = set()
			walk_names(data.get("item", []), found)
			missing = sorted(REQUIRED_REQUEST_NAMES - found)
			if missing:
				errors.append("missing requests: " + ", ".join(missing))
			# Ensure no hard-coded localhost:8080 as API base in raw URLs
			raw = path.read_text(encoding="utf-8")
			if "localhost:8080/api" in raw or "127.0.0.1:8080/api" in raw:
				errors.append("collection appears to hardcode API on :8080; use {{baseUrl}} (:8088)")
		else:
			values = {v["key"]: v.get("value", "") for v in data.get("values", [])}
			if values.get("baseUrl") != "http://localhost:8088/api/v1":
				errors.append(f"baseUrl must be http://localhost:8088/api/v1, got {values.get('baseUrl')!r}")
			for secret_key in ("accessToken", "refreshToken"):
				if values.get(secret_key):
					errors.append(f"environment must not commit non-empty {secret_key}")
			for pwd_key in ("customerPassword", "adminPassword", "registerPassword"):
				val = values.get(pwd_key, "")
				if val and not str(val).startswith("REPLACE_WITH_"):
					errors.append(
						f"{pwd_key} must remain a REPLACE_WITH_* placeholder in committed template"
					)

	if errors:
		print("postman-validate FAILED:")
		for e in errors:
			print(f"  - {e}")
		return 1
	print("OK: postman collection + environment valid")
	print(f"  {COLLECTION.relative_to(ROOT)}")
	print(f"  {ENVIRONMENT.relative_to(ROOT)}")
	return 0


if __name__ == "__main__":
	sys.exit(main())
