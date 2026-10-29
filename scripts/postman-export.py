#!/usr/bin/env python3
"""Generate Postman Collection v2.1 + Environment for Aptech-4 local Compose stack.

Source of truth: backend controllers/DTOs (not guessed). Passwords in the committed
environment file are placeholders — fill from docs/DEMO_SEED.md locally.
"""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "postman"
COLLECTION_PATH = OUT_DIR / "APTECH-4-Local.postman_collection.json"
ENVIRONMENT_PATH = OUT_DIR / "APTECH-4-Local.postman_environment.json"

BASE = "{{baseUrl}}"


def hdr_json() -> list[dict]:
	return [
		{"key": "Content-Type", "value": "application/json"},
		{"key": "Accept", "value": "application/json"},
		{"key": "Origin", "value": "{{webOrigin}}"},
	]


def req(
	name: str,
	method: str,
	path: str,
	*,
	body: dict | None = None,
	auth: bool = False,
	description: str = "",
	tests: str = "",
	query: list[dict] | None = None,
) -> dict:
	url: dict = {
		"raw": f"{BASE}{path}",
		"host": ["{{baseUrl}}"],
		"path": [p for p in path.strip("/").split("/") if p],
	}
	if query:
		url["query"] = []
		parts: list[str] = []
		for q in query:
			val = q["value"]
			url["query"].append(
				{"key": q["key"], "value": val, "description": q.get("description", "")}
			)
			parts.append(f"{q['key']}={val}")
		url["raw"] = f"{BASE}{path}?" + "&".join(parts)

	item: dict = {
		"name": name,
		"request": {
			"method": method,
			"header": hdr_json(),
			"url": url,
			"description": description,
		},
		"response": [],
	}
	if auth:
		item["request"]["auth"] = {
			"type": "bearer",
			"bearer": [{"key": "token", "value": "{{accessToken}}", "type": "string"}],
		}
	if body is not None:
		item["request"]["body"] = {
			"mode": "raw",
			"raw": json.dumps(body, indent=2),
			"options": {"raw": {"language": "json"}},
		}
	if tests:
		item["event"] = [
			{
				"listen": "test",
				"script": {"type": "text/javascript", "exec": tests.strip().split("\n")},
			}
		]
	return item


LOGIN_TESTS = """
pm.test('Login returns 200', function () {
  pm.response.to.have.status(200);
});
const j = pm.response.json();
pm.test('AuthResponse shape', function () {
  pm.expect(j).to.have.property('accessToken');
  pm.expect(j).to.have.property('refreshToken');
  pm.expect(j).to.have.property('tokenType');
  pm.expect(j).to.have.property('user');
  pm.expect(j.user).to.have.property('roles');
  pm.expect(j).to.not.have.property('password');
});
pm.collectionVariables.set('accessToken', j.accessToken);
pm.collectionVariables.set('refreshToken', j.refreshToken);
pm.environment.set('accessToken', j.accessToken);
pm.environment.set('refreshToken', j.refreshToken);
if (j.user && j.user.id) {
  pm.collectionVariables.set('userId', j.user.id);
  pm.environment.set('userId', j.user.id);
}
"""

ADMIN_LOGIN_TESTS = LOGIN_TESTS + """
pm.test('Admin role present', function () {
  pm.expect(j.user.roles).to.include('ADMIN');
});
"""

CUSTOMER_LOGIN_TESTS = LOGIN_TESTS + """
pm.test('Customer role present', function () {
  pm.expect(j.user.roles).to.include('CUSTOMER');
});
"""

REFRESH_TESTS = """
pm.test('Refresh returns 200', function () {
  pm.response.to.have.status(200);
});
const j = pm.response.json();
pm.test('Rotated tokens present', function () {
  pm.expect(j.accessToken).to.be.a('string');
  pm.expect(j.refreshToken).to.be.a('string');
});
pm.collectionVariables.set('accessToken', j.accessToken);
pm.collectionVariables.set('refreshToken', j.refreshToken);
pm.environment.set('accessToken', j.accessToken);
pm.environment.set('refreshToken', j.refreshToken);
"""

PRODUCTS_LIST_TESTS = """
pm.test('Products list 200', function () {
  pm.response.to.have.status(200);
});
const j = pm.response.json();
pm.test('PageResponse shape', function () {
  pm.expect(j).to.have.property('items');
  pm.expect(j.items).to.be.an('array');
  pm.expect(j).to.have.property('page');
  pm.expect(j).to.have.property('totalElements');
});
if (j.items && j.items.length > 0) {
  pm.collectionVariables.set('productId', j.items[0].id);
  pm.environment.set('productId', j.items[0].id);
  if (j.items[0].categoryId) {
    pm.collectionVariables.set('categoryId', j.items[0].categoryId);
    pm.environment.set('categoryId', j.items[0].categoryId);
  }
}
"""

CATEGORIES_TESTS = """
pm.test('Categories 200', function () {
  pm.response.to.have.status(200);
});
const j = pm.response.json();
pm.test('Array body', function () {
  pm.expect(j).to.be.an('array');
});
if (Array.isArray(j) && j.length > 0) {
  pm.collectionVariables.set('categoryId', j[0].id);
  pm.environment.set('categoryId', j[0].id);
}
"""

CART_GET_TESTS = """
pm.test('Cart 200', function () {
  pm.response.to.have.status(200);
});
const j = pm.response.json();
pm.test('Cart shape', function () {
  pm.expect(j).to.have.property('items');
});
if (j.items && j.items.length > 0) {
  pm.collectionVariables.set('cartItemId', j.items[0].id);
  pm.environment.set('cartItemId', j.items[0].id);
}
"""

CART_ADD_TESTS = """
pm.test('Add cart item 200', function () {
  pm.response.to.have.status(200);
});
const j = pm.response.json();
if (j.items && j.items.length > 0) {
  const last = j.items[j.items.length - 1];
  pm.collectionVariables.set('cartItemId', last.id);
  pm.environment.set('cartItemId', last.id);
}
"""

ADDRESS_CREATE_TESTS = """
pm.test('Address create 201 or 200', function () {
  pm.expect([200, 201]).to.include(pm.response.code);
});
const j = pm.response.json();
pm.test('Address has id', function () {
  pm.expect(j).to.have.property('id');
});
pm.collectionVariables.set('addressId', j.id);
pm.environment.set('addressId', j.id);
"""

CHECKOUT_TESTS = """
pm.test('Checkout succeeds', function () {
  pm.expect([200, 201]).to.include(pm.response.code);
});
const j = pm.response.json();
pm.test('CheckoutResponse has orderId', function () {
  pm.expect(j).to.have.property('orderId');
  pm.expect(j).to.have.property('orderStatus');
  pm.expect(j).to.have.property('payment');
});
pm.collectionVariables.set('orderId', j.orderId);
pm.environment.set('orderId', j.orderId);
"""

ORDERS_LIST_TESTS = """
pm.test('Orders list 200', function () {
  pm.response.to.have.status(200);
});
const j = pm.response.json();
const items = j.items || j.content || (Array.isArray(j) ? j : []);
if (items.length > 0 && items[0].id) {
  pm.collectionVariables.set('orderId', items[0].id);
  pm.environment.set('orderId', items[0].id);
}
"""

ADMIN_PRODUCT_CREATE_TESTS = """
pm.test('Admin product create 201 or 200', function () {
  pm.expect([200, 201]).to.include(pm.response.code);
});
const j = pm.response.json();
pm.collectionVariables.set('adminProductId', j.id);
pm.environment.set('adminProductId', j.id);
"""

ADMIN_CATEGORY_CREATE_TESTS = """
pm.test('Admin category create 201 or 200', function () {
  pm.expect([200, 201]).to.include(pm.response.code);
});
const j = pm.response.json();
pm.collectionVariables.set('adminCategoryId', j.id);
pm.environment.set('adminCategoryId', j.id);
"""


def build_collection() -> dict:
	return {
		"info": {
			"_postman_id": "a4e8c0de-1111-4b2c-9f01-aptech4local01",
			"name": "APTECH-4 Local Compose",
			"description": (
				"Local production-like stack via Nginx.\n\n"
				"**Prerequisites**\n"
				"1. `make deploy-local` (or stack already up)\n"
				"2. `make seed-demo` for demo CUSTOMER/ADMIN accounts\n"
				"3. Import this collection + `APTECH-4-Local.postman_environment.json`\n"
				"4. Set `customerPassword` / `adminPassword` from docs/DEMO_SEED.md "
				"(placeholders in the committed environment)\n\n"
				"**baseUrl** must be `http://localhost:8088/api/v1` (not :8080).\n"
				"OpenAPI JSON is on API loopback: `http://127.0.0.1:8080/v3/api-docs` "
				"(not proxied by local nginx).\n\n"
				"**Destructive requests** (logout, soft-disable, cancel, clear cart, checkout) "
				"are labeled — run manually; do not include them in automated Newman smoke "
				"without understanding side effects.\n"
			),
			"schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
		},
		"auth": {
			"type": "bearer",
			"bearer": [{"key": "token", "value": "{{accessToken}}", "type": "string"}],
		},
		"variable": [
			{"key": "accessToken", "value": ""},
			{"key": "refreshToken", "value": ""},
			{"key": "userId", "value": ""},
			{"key": "productId", "value": ""},
			{"key": "categoryId", "value": ""},
			{"key": "cartItemId", "value": ""},
			{"key": "addressId", "value": ""},
			{"key": "orderId", "value": ""},
			{"key": "notificationId", "value": ""},
			{"key": "adminProductId", "value": ""},
			{"key": "adminCategoryId", "value": ""},
		],
		"item": [
			{
				"name": "00-Smoke (safe)",
				"description": "Read-only + login happy path. Safe for Newman when stack is seeded.",
				"item": [
					req(
						"Health",
						"GET",
						"/health",
						description="Public liveness. Expect 200 `{status:OK,service:aptech4-api}`.",
						tests="pm.test('Health 200', () => pm.response.to.have.status(200));\n"
						"const j = pm.response.json();\n"
						"pm.test('status OK', () => pm.expect(j.status).to.eql('OK'));",
					),
					req(
						"List categories",
						"GET",
						"/categories",
						description="Public active categories.",
						tests=CATEGORIES_TESTS,
					),
					req(
						"List products (page)",
						"GET",
						"/products",
						query=[
							{"key": "page", "value": "0"},
							{"key": "size", "value": "5"},
							{"key": "sort", "value": "name,asc"},
						],
						description="Public products. Saves `productId` from first item.",
						tests=PRODUCTS_LIST_TESTS,
					),
					req(
						"Login customer",
						"POST",
						"/auth/login",
						body={"email": "{{customerEmail}}", "password": "{{customerPassword}}"},
						description="Requires seeded demo customer. Saves access/refresh tokens.",
						tests=CUSTOMER_LOGIN_TESTS,
					),
				],
			},
			{
				"name": "01-Public",
				"item": [
					req("Health", "GET", "/health", description="GET /api/v1/health"),
					req(
						"List categories",
						"GET",
						"/categories",
						tests=CATEGORIES_TESTS,
					),
					req(
						"Get category by id",
						"GET",
						"/categories/{{categoryId}}",
						description="Requires `categoryId` from list. Inactive/missing → 404.",
						tests="pm.test('Category detail 200', () => pm.response.to.have.status(200));",
					),
					req(
						"List products",
						"GET",
						"/products",
						query=[
							{"key": "page", "value": "0"},
							{"key": "size", "value": "20"},
							{"key": "sort", "value": "name,asc"},
						],
						tests=PRODUCTS_LIST_TESTS,
					),
					req(
						"Search/filter products",
						"GET",
						"/products",
						query=[
							{"key": "q", "value": "Demo"},
							{"key": "page", "value": "0"},
							{"key": "size", "value": "10"},
							{"key": "sort", "value": "price,asc"},
							{"key": "inStock", "value": "true"},
							{"key": "minPrice", "value": "0"},
							{"key": "maxPrice", "value": "20000000"},
						],
						description="Filters: q, categoryId, minPrice, maxPrice, inStock, page, size, sort (name|price|createdAt).",
						tests=PRODUCTS_LIST_TESTS,
					),
					req(
						"Get product by id",
						"GET",
						"/products/{{productId}}",
						description="Inactive product/category → 404.",
						tests="pm.test('Product detail 200', () => pm.response.to.have.status(200));",
					),
				],
			},
			{
				"name": "02-Auth",
				"item": [
					req(
						"Register customer",
						"POST",
						"/auth/register",
						body={
							"email": "{{registerEmail}}",
							"password": "{{registerPassword}}",
							"fullName": "Postman Register User",
						},
						description="Always creates CUSTOMER (201). Client cannot choose ADMIN. "
						"Change `registerEmail` each run to avoid 409.",
						tests="""
pm.test('Register 201', () => pm.response.to.have.status(201));
const j = pm.response.json();
pm.expect(j.user.roles).to.include('CUSTOMER');
pm.collectionVariables.set('accessToken', j.accessToken);
pm.collectionVariables.set('refreshToken', j.refreshToken);
pm.environment.set('accessToken', j.accessToken);
pm.environment.set('refreshToken', j.refreshToken);
""",
					),
					req(
						"Login customer",
						"POST",
						"/auth/login",
						body={"email": "{{customerEmail}}", "password": "{{customerPassword}}"},
						tests=CUSTOMER_LOGIN_TESTS,
					),
					req(
						"Login admin",
						"POST",
						"/auth/login",
						body={"email": "{{adminEmail}}", "password": "{{adminPassword}}"},
						description="Seeded ADMIN. Overwrites collection tokens with admin session.",
						tests=ADMIN_LOGIN_TESTS,
					),
					req(
						"Refresh tokens",
						"POST",
						"/auth/refresh",
						body={"refreshToken": "{{refreshToken}}"},
						description="Rotates refresh token; old refresh becomes invalid.",
						tests=REFRESH_TESTS,
					),
					req(
						"Logout (destructive session)",
						"POST",
						"/auth/logout",
						body={"refreshToken": "{{refreshToken}}"},
						description="**Destructive for current refresh session.** Expect 204. "
						"Access JWT remains valid until exp. Re-login after this.",
						tests="pm.test('Logout 204', () => pm.response.to.have.status(204));",
					),
				],
			},
			{
				"name": "03-Customer",
				"description": "Requires CUSTOMER Bearer token (Login customer first).",
				"item": [
					req("Get cart", "GET", "/cart", auth=True, tests=CART_GET_TESTS),
					req(
						"Add cart item",
						"POST",
						"/cart/items",
						auth=True,
						body={"productId": "{{productId}}", "quantity": 1},
						description="Requires `productId` from public product list.",
						tests=CART_ADD_TESTS,
					),
					req(
						"Update cart item qty",
						"PATCH",
						"/cart/items/{{cartItemId}}",
						auth=True,
						body={"quantity": 2},
						tests="pm.test('Update cart 200', () => pm.response.to.have.status(200));",
					),
					req(
						"Remove cart item",
						"DELETE",
						"/cart/items/{{cartItemId}}",
						auth=True,
						description="Removes one line.",
						tests="pm.test('Remove item 200', () => pm.response.to.have.status(200));",
					),
					req(
						"Clear cart (destructive)",
						"DELETE",
						"/cart/items",
						auth=True,
						description="**Clears all cart lines** for the current customer.",
						tests="pm.test('Clear cart 200', () => pm.response.to.have.status(200));",
					),
					req(
						"List addresses",
						"GET",
						"/addresses",
						auth=True,
						tests="""
pm.test('Addresses 200', () => pm.response.to.have.status(200));
const j = pm.response.json();
const items = Array.isArray(j) ? j : (j.items || []);
if (items.length > 0) {
  pm.collectionVariables.set('addressId', items[0].id);
  pm.environment.set('addressId', items[0].id);
}
""",
					),
					req(
						"Create address",
						"POST",
						"/addresses",
						auth=True,
						body={
							"label": "Home",
							"recipientName": "Demo Customer",
							"line1": "123 Demo Street",
							"line2": "",
							"city": "Ho Chi Minh",
							"stateRegion": "",
							"postalCode": "700000",
							"countryCode": "VN",
							"phone": "+84900000000",
							"defaultAddress": True,
						},
						tests=ADDRESS_CREATE_TESTS,
					),
					req(
						"Checkout (mock payment)",
						"POST",
						"/checkout",
						auth=True,
						body={
							"idempotencyKey": "{{$guid}}",
							"addressId": "{{addressId}}",
							"simulateFailure": False,
						},
						description="**Creates a real order** (mock payment). Cart must be non-empty. "
						"`simulateFailure` honored only when server allows client force-failure.",
						tests=CHECKOUT_TESTS,
					),
					req(
						"List my orders",
						"GET",
						"/orders",
						auth=True,
						query=[{"key": "page", "value": "0"}, {"key": "size", "value": "20"}],
						tests=ORDERS_LIST_TESTS,
					),
					req(
						"Get my order",
						"GET",
						"/orders/{{orderId}}",
						auth=True,
						tests="pm.test('Order detail 200', () => pm.response.to.have.status(200));",
					),
					req(
						"Cancel my order (PENDING_PAYMENT only)",
						"POST",
						"/orders/{{orderId}}/cancel",
						auth=True,
						description="**Destructive.** Customer cancel allowed only for PENDING_PAYMENT. "
						"PAID orders → error. Do not run blindly after successful mock checkout.",
						tests="pm.test('Cancel responded', () => pm.expect(pm.response.code).to.be.oneOf([200, 409, 400]));",
					),
					req(
						"List my notifications",
						"GET",
						"/notifications",
						auth=True,
						query=[{"key": "page", "value": "0"}, {"key": "size", "value": "20"}],
						description="Recipient-scoped feed (CUSTOMER or ADMIN). Newest first.",
						tests="""
pm.test('Notifications list 200', () => pm.response.to.have.status(200));
const j = pm.response.json();
pm.expect(j).to.have.property('items');
if (j.items && j.items.length > 0) {
  pm.collectionVariables.set('notificationId', j.items[0].id);
  pm.environment.set('notificationId', j.items[0].id);
}
""",
					),
					req(
						"Unread notification count",
						"GET",
						"/notifications/unread-count",
						auth=True,
						tests="pm.test('Unread count 200', () => pm.response.to.have.status(200));",
					),
					req(
						"Mark notification read",
						"PATCH",
						"/notifications/{{notificationId}}/read",
						auth=True,
						tests="pm.test('Mark read responded', () => pm.expect(pm.response.code).to.be.oneOf([200, 404]));",
					),
					req(
						"Mark all notifications read",
						"PATCH",
						"/notifications/read-all",
						auth=True,
						tests="pm.test('Mark all read 200', () => pm.response.to.have.status(200));",
					),
				],
			},
			{
				"name": "04-Admin",
				"description": "Requires ADMIN Bearer token (Login admin first). Soft-disable is not hard delete.",
				"item": [
					req(
						"Admin list products",
						"GET",
						"/admin/products",
						auth=True,
						query=[{"key": "page", "value": "0"}, {"key": "size", "value": "20"}],
						tests="""
pm.test('Admin products 200', () => pm.response.to.have.status(200));
const j = pm.response.json();
const items = j.items || [];
if (items.length > 0) {
  pm.collectionVariables.set('adminProductId', items[0].id);
  pm.environment.set('adminProductId', items[0].id);
}
""",
					),
					req(
						"Admin get product",
						"GET",
						"/admin/products/{{adminProductId}}",
						auth=True,
						tests="pm.test('Admin product 200', () => pm.response.to.have.status(200));",
					),
					req(
						"Admin create product",
						"POST",
						"/admin/products",
						auth=True,
						body={
							"sku": "POSTMAN-TMP-{{$timestamp}}",
							"name": "Postman Temp Product",
							"description": "Created by Postman; soft-disable after tests",
							"unitPrice": 99000.0,
							"currency": "VND",
							"categoryId": "{{categoryId}}",
						},
						description="Requires active `categoryId`. Creates a new SKU — soft-disable when done.",
						tests=ADMIN_PRODUCT_CREATE_TESTS,
					),
					req(
						"Admin update product",
						"PUT",
						"/admin/products/{{adminProductId}}",
						auth=True,
						body={
							"sku": "POSTMAN-TMP-UPDATED",
							"name": "Postman Temp Product Updated",
							"description": "Updated by Postman",
							"unitPrice": 109000.0,
							"currency": "VND",
							"categoryId": "{{categoryId}}",
						},
						tests="pm.test('Admin update 200', () => pm.response.to.have.status(200));",
					),
					req(
						"Admin product status (soft-disable)",
						"PATCH",
						"/admin/products/{{adminProductId}}/status",
						auth=True,
						body={"active": False},
						description="**Soft-disable only** (no hard DELETE). Sets active=false.",
						tests="pm.test('Status patch 200', () => pm.response.to.have.status(200));",
					),
					req(
						"Admin list categories",
						"GET",
						"/admin/categories",
						auth=True,
						tests="""
pm.test('Admin categories 200', () => pm.response.to.have.status(200));
const j = pm.response.json();
const items = Array.isArray(j) ? j : [];
if (items.length > 0) {
  pm.collectionVariables.set('adminCategoryId', items[0].id);
  pm.environment.set('adminCategoryId', items[0].id);
}
""",
					),
					req(
						"Admin create category",
						"POST",
						"/admin/categories",
						auth=True,
						body={
							"name": "Postman Cat {{$timestamp}}",
							"description": "Created by Postman",
							"parentId": None,
						},
						tests=ADMIN_CATEGORY_CREATE_TESTS,
					),
					req(
						"Admin update category",
						"PUT",
						"/admin/categories/{{adminCategoryId}}",
						auth=True,
						body={
							"name": "Postman Cat Updated",
							"description": "Updated by Postman",
							"parentId": None,
						},
						tests="pm.test('Admin category update 200', () => pm.response.to.have.status(200));",
					),
					req(
						"Admin category status (soft-disable)",
						"PATCH",
						"/admin/categories/{{adminCategoryId}}/status",
						auth=True,
						body={"active": False},
						description="**Soft-disable.** Do not disable shared demo categories in a shared DB.",
						tests="pm.test('Category status 200', () => pm.response.to.have.status(200));",
					),
					req(
						"Admin list orders",
						"GET",
						"/admin/orders",
						auth=True,
						query=[{"key": "page", "value": "0"}, {"key": "size", "value": "20"}],
						tests=ORDERS_LIST_TESTS,
					),
					req(
						"Admin get order",
						"GET",
						"/admin/orders/{{orderId}}",
						auth=True,
						tests="pm.test('Admin order 200', () => pm.response.to.have.status(200));",
					),
					req(
						"Admin update order status",
						"PATCH",
						"/admin/orders/{{orderId}}/status",
						auth=True,
						body={"status": "PROCESSING"},
						description="**Changes fulfillment status.** Allowed transitions per ADR-008 "
						"(PENDING_PAYMENT, PAID, PROCESSING, SHIPPED, COMPLETED, CANCELLED, FAILED). "
						"Admin cancel does not refund/restock.",
						tests="pm.test('Status update responded', () => pm.expect(pm.response.code).to.be.oneOf([200, 409, 400]));",
					),
				],
			},
		],
	}


def build_environment() -> dict:
	values = [
		{"key": "baseUrl", "value": "http://localhost:8088/api/v1", "type": "default", "enabled": True},
		{"key": "webOrigin", "value": "http://localhost:8088", "type": "default", "enabled": True},
		{"key": "webUrl", "value": "http://localhost:8088", "type": "default", "enabled": True},
		{"key": "apiLoopbackUrl", "value": "http://127.0.0.1:8080", "type": "default", "enabled": True},
		{
			"key": "customerEmail",
			"value": "customer@aptech4.local",
			"type": "default",
			"enabled": True,
		},
		{
			"key": "customerPassword",
			"value": "REPLACE_WITH_DEMO_CUSTOMER_PASSWORD",
			"type": "secret",
			"enabled": True,
		},
		{"key": "adminEmail", "value": "admin@aptech4.local", "type": "default", "enabled": True},
		{
			"key": "adminPassword",
			"value": "REPLACE_WITH_DEMO_ADMIN_PASSWORD",
			"type": "secret",
			"enabled": True,
		},
		{
			"key": "registerEmail",
			"value": "postman.register@aptech4.local",
			"type": "default",
			"enabled": True,
		},
		{
			"key": "registerPassword",
			"value": "REPLACE_WITH_LOCAL_REGISTER_PASSWORD",
			"type": "secret",
			"enabled": True,
		},
		{"key": "accessToken", "value": "", "type": "secret", "enabled": True},
		{"key": "refreshToken", "value": "", "type": "secret", "enabled": True},
		{"key": "userId", "value": "", "type": "default", "enabled": True},
		{"key": "productId", "value": "", "type": "default", "enabled": True},
		{"key": "categoryId", "value": "", "type": "default", "enabled": True},
		{"key": "cartItemId", "value": "", "type": "default", "enabled": True},
		{"key": "addressId", "value": "", "type": "default", "enabled": True},
		{"key": "orderId", "value": "", "type": "default", "enabled": True},
		{"key": "notificationId", "value": "", "type": "default", "enabled": True},
		{"key": "adminProductId", "value": "", "type": "default", "enabled": True},
		{"key": "adminCategoryId", "value": "", "type": "default", "enabled": True},
	]
	return {
		"id": "b1c2d3e4-aptech4-local-env-0001",
		"name": "APTECH-4 Local",
		"values": values,
		"_postman_variable_scope": "environment",
		"_postman_exported_using": "aptech-4/scripts/postman-export.py",
	}


def main() -> None:
	OUT_DIR.mkdir(parents=True, exist_ok=True)
	collection = build_collection()
	environment = build_environment()
	COLLECTION_PATH.write_text(json.dumps(collection, indent=2) + "\n", encoding="utf-8")
	ENVIRONMENT_PATH.write_text(json.dumps(environment, indent=2) + "\n", encoding="utf-8")

	# Count requests
	def count_items(items: list) -> int:
		n = 0
		for it in items:
			if "item" in it:
				n += count_items(it["item"])
			else:
				n += 1
		return n

	n = count_items(collection["item"])
	print(f"Wrote {COLLECTION_PATH.relative_to(ROOT)} ({n} requests)")
	print(f"Wrote {ENVIRONMENT_PATH.relative_to(ROOT)}")
	print("Passwords in environment are PLACEHOLDERS — set from docs/DEMO_SEED.md (local demo only).")


if __name__ == "__main__":
	main()
