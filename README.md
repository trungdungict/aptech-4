# Aptech-4

Aptech semester 4 **e-commerce graduation project**: Spring Boot API, React storefront/admin, Flutter Android customer app, PostgreSQL, Redis, Docker Compose, Makefile, and EC2 demo deployment.

## Status

Implementation is driven by the persistent Cursor playbook — not by chat history.

- Playbook: [`.cursor/PROJECT_PLAYBOOK.md`](.cursor/PROJECT_PLAYBOOK.md)
- Tasks: [`.cursor/state/TASKS.md`](.cursor/state/TASKS.md)
- Progress: [`.cursor/state/PROGRESS.md`](.cursor/state/PROGRESS.md)

## Target layout

```
backend/     Spring Boot 4.1.2 + Java 21 (Maven Wrapper)
frontend/    React + TypeScript (Vite) — customer + admin
mobile/      Flutter Android customer app
deploy/      Nginx + EC2 scripts
docs/        Technical and demo documentation
Makefile     Local, demo, APK, and EC2 workflows
```

## Prerequisites (host)

| Tool | Required for |
|------|----------------|
| Docker + Compose | Postgres/Redis and prod-like local stack |
| JDK 21 | Native backend build/test (or use Docker Temurin) |
| Node.js + npm | Frontend |
| **FVM** + pinned Flutter | Android app + APK (do **not** rely on a global Flutter) |
| Android SDK | Device/emulator/APK (see `make doctor`) |
| Make + Git | Root workflows |
| Homebrew (macOS) | Optional path to install FVM via Make |
| SSH key + EC2 host | Remote deploy (optional, approval-gated) |

Copy env template: `cp .env.example .env`

Local deps + demo seed (does **not** wipe volumes):

```bash
make up-deps
make seed-check                # dry preflight + read-only demo counts
make seed-demo                 # idempotent upsert; see docs/DEMO_SEED.md
make smoke-demo                # login + catalog via stack nginx (:8088)
make reset-demo-dry            # print demo delete scope (no deletes)
make reset-demo CONFIRM=yes    # scoped demo reset + reseed (not volume wipe)
make down                      # stop Compose services; keeps volumes
make deploy-local-ai           # optional Ollama + AI chat (see docs/AI_ASSISTANT.md)
make up-ai && make ollama-pull # pull model without wiping DB volumes
make monitoring-up             # optional Prometheus/Grafana (docs/MONITORING.md)
make clean                     # Maven + Flutter build artifacts (not DB/volumes)
```

Seed/reset are **one-shot** host Spring runs (`SERVER_PORT=18081` by default) against Compose Postgres. They do **not** enable seed on the long-running `api` container.

Production-like local stack (API + nginx web + Postgres + Redis; ADR-016):

```bash
make build-stack               # multi-stage API + web images
make deploy-local              # compose profile `stack`; WEB_PORT=8088
make health-local
make smoke-local               # web `/` + `/api/v1/health` + `/api/v1/categories`
make logs-local                # api + web
make down-local                # stop api/web; volumes kept
```

Web UI: `http://localhost:8088` — browser calls **same-origin** `/api/v1` and STOMP `/ws` (baked at image build; not runtime-mutable). Host Vite still uses `VITE_API_BASE_URL=http://localhost:8080/api/v1` (optional `/ws` proxy in `vite.config.ts`).

Demo login (after `make seed-demo`): see [docs/DEMO_SEED.md](docs/DEMO_SEED.md). Compose `api` CORS always allows `http://localhost:8088` (JSON POSTs send `Origin`; missing allow-list → 403).

Postman (importable Collection + Environment for the Nginx API):

```bash
make postman-export
make postman-info              # URLs + documented demo accounts (not .env secrets)
make postman-help
# Optional: make postman-smoke   # Newman folder 00-Smoke if npx available
```

Details: [postman/README.md](postman/README.md).

Frontend scaffold (`frontend/` — see [frontend/README.md](frontend/README.md)):

```bash
cd frontend && npm install && npm run typecheck && npm test && npm run build
```

### Flutter / Shorebird (Android) — Docker preferred

Details: [mobile/README.md](mobile/README.md). Image pins Flutter **3.47.8** + Android SDK + Shorebird CLI. OTA uses Shorebird Cloud (`SHOREBIRD_TOKEN`); no self-hosted OTA server.

```bash
make mobile-docker-check
make mobile-build-image
make mobile-doctor
make mobile-create CONFIRM=yes          # once; refuses overwrite
make mobile-pub-get && make mobile-analyze && make mobile-test
make mobile-build-apk API_BASE_URL=http://10.0.2.2:8080/api/v1
# → mobile/build/app/outputs/flutter-apk/app-debug.apk (gitignored)
# OTA (gated; needs token):
# export SHOREBIRD_TOKEN=…              # from console — never commit
# make mobile-shorebird-init
# make mobile-shorebird-release CONFIRM=yes
# make mobile-shorebird-patch CONFIRM=yes
```

Optional host FVM path: `make check-tools` / `install-tools CONFIRM=yes` (see `mobile/.fvmrc.example`).

## Resume in a new Cursor Agent session

```
Resume Aptech-4 from the repository playbook (do not rely on chat history).

Project root: /Users/Martin/Documents/APTECT_PROJECT_4/aptech-4

1. Read .cursor/PROJECT_PLAYBOOK.md
2. Read .cursor/state/PROGRESS.md, TASKS.md, CURRENT_SESSION.md, BLOCKERS.md
3. Read the relevant phase plan under .cursor/plans/
4. Inspect git status and reconcile tracking vs code
5. Continue the exact next task recorded in PROGRESS.md / CURRENT_SESSION.md
6. Verify with the task's commands, update state files, checkpoint before stopping

Follow .cursor/rules/ at all times.
```

## License

See [LICENSE](LICENSE).
