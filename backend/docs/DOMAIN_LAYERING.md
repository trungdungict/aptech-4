# Backend domain layering

Modular monolith packages under `com.aptech.aptech4`:

| Package | Responsibility |
|---------|----------------|
| `identity.domain` | Users, roles, addresses, refresh tokens |
| `catalog.domain` / `catalog.api` / `catalog.application` / `catalog.persistence` | Categories, products, inventory + stock reservations (ADR-006) |
| `cart.domain` / `cart.api` / `cart.application` / `cart.persistence` | Carts and cart lines (customer ownership; no stock reservation) |
| `order.domain` / `order.api` / `order.application` / `order.payment` / `order.persistence` | Checkout, orders, lifecycle, mock payments (ADR-007, ADR-008) |
| `api` | Cross-cutting HTTP adapters (e.g. health); feature APIs live in module `*.api` packages |
| `config` | Cross-cutting Spring configuration |

Category soft-disable uses `categories.active` (Liquibase `080`). Product soft-disable uses `products.active`. Public APIs never expose inactive rows; admin uses status PATCH instead of hard delete. Product admin CRUD does **not** create inventory rows — ADMIN sets stock via `GET/PUT /api/v1/admin/products/{id}/inventory` (`AdminInventoryService`). Stock holds use `quantity_reserved` + `stock_reservations` (Liquibase `100`, ADR-006); cart never reserves; checkout confirms via service in P06-T03.

## Rules

- Schema is owned by **Liquibase**; `spring.jpa.hibernate.ddl-auto=validate` only.
- Entities map 1:1 to tables from changelogs `010`–`070` (ADR-004).
- Associations default to `FetchType.LAZY`.
- Aggregate cascades: cart→items, order→items/payments. Do **not** cascade-remove from `User` to historical `Order`/`Payment`.
- Sensitive columns (`password_hash`, `token_hash`) must never appear in `toString`, logs, or API DTOs.
