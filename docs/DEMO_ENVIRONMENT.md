# Local demo environment (FoodMart + Compose)

Fully reproducible **local-only** demo stack for Aptech-4. Production profiles refuse seed/reset.

## Command semantics

| Command | Stops services | Deletes Docker volumes | Seeds data |
|---------|----------------|------------------------|------------|
| `make down` | stack + ai + monitoring | **No** | No |
| `make down-local` | api + web | **No** | No |
| `make down-deps` | postgres + redis | **No** | No |
| `make down-all CONFIRM=yes` | all profiles | **Yes** (pg/redis/ollama/prometheus/grafana) | No |
| `make deploy-local` | — | **No** | **No** (core only; AI off) |
| `make deploy-demo-stack` | — | **No** | **No** (core + AI + monitoring) |
| `make seed-demo` | — | **No** | Idempotent upsert into Compose Postgres |
| `make up-all` | — | **No** | Full demo: stack+AI+monitoring → model → seed → smokes |
| `make reset-demo CONFIRM=yes` | — | **No** | Deletes **demo-scoped** rows only, then reseeds |

`make down` / `make restart`-style targets never wipe volumes. Only `down-all CONFIRM=yes` and `reset-deps-volumes` remove data volumes.

### `make up-all` services

| Area | Services |
|------|----------|
| Core | `postgres`, `redis`, `api`, `web` |
| AI chatbot | `ollama` (+ API with `APTECH_AI_ENABLED=true`, `SPRING_AI_MODEL_CHAT=ollama`, `OLLAMA_BASE_URL=http://ollama:11434`) |
| Monitoring | `prometheus`, `grafana`, `postgres-exporter`, `redis-exporter`, `cadvisor` |

`up-all` **fails** if Ollama model pull fails, chat is not `READY`, or **required** Prometheus targets (`prometheus`, `aptech4-api`, `postgres-exporter`, `redis-exporter`) are not `up`. Grafana must be healthy. **cAdvisor** scrape failure on Docker Desktop (macOS / WSL2) is a **warning only** and does not fail `up-all`.

## Windows 11 + WSL2 (Docker Desktop)

Run the demo from **Ubuntu WSL**, not PowerShell or CMD.

1. **Docker Desktop** → Settings → General: use the WSL2 engine; enable integration for your Ubuntu distro. Confirm in Ubuntu: `docker info`.
2. **Clone on the Linux filesystem** (e.g. `~/src/aptech-4`), **not** under `/mnt/c/...` (bind mounts and CRLF issues).
3. **Host JDK 21** in WSL is required because `make seed-demo` runs `./mvnw` on the host against Compose Postgres/Redis. Set `JAVA_HOME` to that JDK (Temurin/OpenJDK). Do not rely on macOS Homebrew paths.
4. Also install in WSL: `make`, `git`, `curl`, `python3`, `bash`.
5. Copy env and set secrets:
   ```bash
   cp .env.example .env
   # Edit DEMO_* passwords, JWT_* secrets (≥32 chars, access ≠ refresh), Grafana password as needed.
   ```
6. Rebuild:
   ```bash
   make down-all CONFIRM=yes
   make up-all
   ```
7. Open from Windows browser (Docker publishes to localhost): storefront http://localhost:8088, Grafana http://127.0.0.1:3001, Prometheus http://127.0.0.1:9090, Ollama http://127.0.0.1:11434.

macOS Homebrew OpenJDK is used only when `/opt/homebrew/opt/openjdk@21/bin/java` exists; WSL/Linux leave `JAVA_HOME` / `PATH` unchanged unless you export them.

## Clean rebuild (Stage B — requires explicit approval)

```bash
make down-all CONFIRM=yes
make up-all
```

After `up-all`:

1. Compose builds/starts **stack + ai + monitoring** (`deploy-demo-stack`) with AI enabled.
2. Liquibase migrations run inside the API; health waits for api/web/ollama/prometheus/grafana.
3. `ollama-ensure-model` pulls `APTECH_AI_MODEL` (default `llama3.2`) if missing — may download GBs; failure aborts `up-all`.
4. Host one-shot Spring process (`make seed-demo`) upserts FoodMart demo data (port `SEED_SERVER_PORT`, default 18081).
5. `smoke-demo` verifies login + FoodMart catalog via `http://localhost:8088`.
6. `smoke-chat` verifies JWT chat health `READY` + one completion.
7. `monitoring-validate-demo` verifies Prometheus/Grafana and required scrape targets up (cAdvisor optional warn on Docker Desktop).

### Local URLs

| UI / API | URL |
|----------|-----|
| Storefront + API (nginx) | http://localhost:8088 |
| API loopback (optional) | http://127.0.0.1:8080 |
| Ollama | http://127.0.0.1:11434 |
| Prometheus | http://127.0.0.1:9090 |
| Grafana | http://127.0.0.1:3001 |

### Required environment (local demo)

See `.env.example`. Critical for `up-all`:

- `DEMO_ADMIN_*` / `DEMO_CUSTOMER_*` — seed + smoke login
- `JWT_ACCESS_SECRET` / `JWT_REFRESH_SECRET` — API auth (≥32 chars, distinct)
- `APTECH_AI_MODEL` — default `llama3.2` (pulled into `aptech4_ollama`)
- `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` — Grafana login (defaults in `.env.example`)

Production must keep AI/seed off; Compose API seed remains `APTECH_SEED_ENABLED=false` on the long-running container.

## Seed architecture

Source of truth for **persisted** catalog/journey data:

- `backend/src/main/resources/db/demo/foodmart-catalog.json` — 8 grocery categories + 48 `FOOD-*` products + `onHand` (sole catalog SoT)
- `DemoSeedService` + `DemoJourneySeedService` — upsert users, FoodMart catalog, inventory, addresses, cart, orders, payments, notifications
- Retired legacy `DEMO-*` / `demo-*` keys are purged on seed via `DemoResetService.purgeRetiredCatalog()` (local-demo guard only)

Frontend presentation only:

- `frontend/src/features/catalog/data/foodmart-image-map.json` — SKU → `/assets/foodmart/...`
- `frontend/src/features/catalog/data/foodmart-demo-data.json` — mirror for UI tooling / import dry-run (not the DB source of truth)

Images are **not** stored in the database (`imageUrl` remains null).

## Environment variables

Repo-root `.env` (gitignored) drives seed Make targets:

```env
DEMO_ADMIN_EMAIL=admin@aptech4.local
DEMO_ADMIN_PASSWORD=admin
DEMO_CUSTOMER_EMAIL=customer@aptech4.local
DEMO_CUSTOMER_PASSWORD=customer
```

`frontend/.env` uses the same Admin credentials for `npm run import:foodmart-demo` (optional; backend seed is primary).

Explicit shell exports override `.env` files. Never commit secrets. Seed refuses Spring profile `prod`.

**Password note:** Seed accepts local demo passwords ≥5 characters so `admin` / `customer` work. Public registration still requires ≥8. Existing user password hashes are **not** overwritten by re-seed — use a clean volume (`down-all`) or `reset-demo CONFIRM=yes` to recreate demo users with current env passwords.

## Demo accounts

| Role | Email | Password (local default) |
|------|-------|--------------------------|
| ADMIN | `admin@aptech4.local` | `admin` |
| CUSTOMER | `customer@aptech4.local` | `customer` |
| CUSTOMER | `lan.nguyen@aptech4.local` | same as `DEMO_CUSTOMER_PASSWORD` |

## Expected counts after seed (approx.)

| Entity | Count / notes |
|--------|----------------|
| Roles | 2 (`ADMIN`, `CUSTOMER`) from Liquibase |
| Demo users | 3 |
| Categories | **8** FoodMart (`food-*`) |
| Products | **48** `FOOD-*` (no `DEMO-*`) |
| Inventory rows | **48** (includes OOS / low stock FoodMart SKUs) |
| Addresses | ≥2 |
| Sample orders | ≥6 lifecycle states |
| Notifications | ≥4 (incl. journey marker) |

## Inventory

`onHand` from fixtures → `inventory.quantity_on_hand`; reserved left at 0 unless already higher. Available stock = on_hand − reserved. Frontend `inStock` reflects that after seed (unlike Admin API create without inventory).

## Adding mock data

1. Extend `db/demo/foodmart-catalog.json` only (FoodMart is the catalog SoT).
2. Add SKU → image in `foodmart-image-map.json` if storefront imagery is needed.
3. Extend `DemoSeedMarkers` (active or retired lists) if reset/purge must cover new natural keys.
4. Run `make seed-demo` (idempotent; also purges retired catalog keys) or clean rebuild.

## Production isolation

- `APTECH_SEED_ENABLED=false` on the long-running Compose API.
- `DemoSeedGuard` refuses `prod`.
- No demo passwords in production config.
- Volume wipe is never part of ordinary `down` / `deploy-local`.
