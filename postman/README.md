# Postman — Aptech-4 local Compose stack

Importable Collection v2.1 + Environment for API testing against the production-like local stack.

## Files

| File | Purpose |
|------|---------|
| `APTECH-4-Local.postman_collection.json` | Requests grouped Public / Auth / Customer / Admin (+ safe Smoke folder) |
| `APTECH-4-Local.postman_environment.json` | `baseUrl` + placeholders (no live tokens) |

Regenerate:

```bash
make postman-export
make postman-validate
make postman-info
make postman-help
```

## Import into Postman

1. Postman → **Import** → select both JSON files under `postman/`.
2. Select environment **APTECH-4 Local**.
3. Set secrets (from `docs/DEMO_SEED.md` / `make postman-info`):
   - `customerPassword` → documented CUSTOMER demo password
   - `adminPassword` → documented ADMIN demo password
   - optionally `registerPassword` + unique `registerEmail` for register tests
4. Confirm `baseUrl` = `http://localhost:8088/api/v1` (Nginx stack, **not** `:8080`).

## Suggested flow

1. Ensure stack + seed: `make deploy-local && make seed-demo`
2. Folder **00-Smoke (safe)**: Health → categories → products → Login customer
3. **03-Customer**: Add to cart → create address → checkout (creates a real mock order)
4. Switch tokens: **02-Auth → Login admin**
5. **04-Admin**: list products/categories/orders

Login/register/refresh scripts save `accessToken` / `refreshToken` into collection + environment variables. Bearer auth uses `{{accessToken}}`.

## Security

- Committed environment uses `REPLACE_WITH_*` password placeholders and **empty** tokens.
- Do **not** commit Postman exports after runs that contain live JWTs.
- Do not put `SHOREBIRD_TOKEN`, DB passwords, or JWT signing secrets into Postman.
- Demo passwords are local-only; never use in production.

## OpenAPI / Swagger

Available on API loopback only (not via Nginx `:8088`):

- `http://127.0.0.1:8080/v3/api-docs`
- `http://127.0.0.1:8080/swagger-ui/index.html`

## Optional Newman smoke

```bash
make postman-smoke
```

Runs folder **00-Smoke (safe)** via `npx newman` when available. Skips cleanly if Newman/npx is missing. Does not soft-disable products, cancel orders, or wipe carts.
