# Aptech-4 root Makefile — local deps first; more targets added in later phases.
# Local deploy must never require AWS credentials.
# EC2 targets (later) must require EC2_HOST and must not default to localhost.
# Flutter/Android mobile tooling uses FVM (not a global Flutter SDK). See mobile/README.md.

SHELL := /bin/bash
.DEFAULT_GOAL := help

COMPOSE := docker compose
COMPOSE_STACK := $(COMPOSE) --profile stack
COMPOSE_MONITORING := $(COMPOSE) --profile monitoring
# Complete local demo: core app + Ollama AI + Prometheus/Grafana/exporters
COMPOSE_DEMO := $(COMPOSE) --profile stack --profile ai --profile monitoring
ENV_FILE := .env
MOBILE_DIR := mobile
DEV_FLUTTER := ./scripts/dev-flutter.sh
MOBILE_DOCKER := ./scripts/mobile-docker.sh
CHECK_DOTENV := ./scripts/check-dotenv.sh
# Gate for install-tools / shorebird release|patch / flutter create (CONFIRM=yes)
CONFIRM ?=
# Optional device id for `make run DEVICE=...`
DEVICE ?=
# Optional API URL for Flutter run/apk — forwarded into --dart-define only (public config).
# Empty here → scripts/mobile-docker.sh uses .env then emulator default (10.0.2.2).
API_BASE_URL ?=
# Pinned Flutter used by the mobile Docker image tag
FLUTTER_VERSION ?= 3.47.8
# Local stack web publish port (nginx). API is loopback-only by default (API_PUBLISH).
WEB_PORT ?= 8088
# One-shot seed/reset Spring Boot port — must NOT collide with stack API on :8080.
SEED_SERVER_PORT ?= 18081
# Do not `include` .env in Make (values may break Make syntax / leak via -d).
# Shell recipes and scripts/mobile-docker.sh source .env instead; never echo secrets.

.PHONY: help env-check up-deps down-deps down down-all up-all clean ps logs-deps health-deps reset-deps-volumes \
	build-stack deploy-local deploy-demo-stack deploy-local-ai down-local health-local health-demo-stack \
	logs-local smoke-local smoke-demo smoke-chat smoke-product-image-upload \
	test-product-image-upload test-nginx-assets-no-spa-fallback \
	up-ai ollama-pull ollama-ensure-model ollama-health down-ai \
	monitoring-up monitoring-down monitoring-status monitoring-validate monitoring-validate-demo \
	invalidate-catalog-cache \
	seed-check seed-demo reset-demo-dry reset-demo \
	check-up-all-makefile test-monitoring-validate-demo \
	postman-export postman-validate postman-info postman-help postman-smoke \
	check-tools check-env check-env-flutter install-tools setup deps get doctor run test analyze status \
	clean-mobile \
	mobile-build-image mobile-doctor mobile-versions mobile-shorebird-auth mobile-pub-get \
	mobile-analyze mobile-test mobile-build-android mobile-build-apk mobile-build-release-apk \
	mobile-build-release-aab mobile-create mobile-shorebird-init mobile-shorebird-workflow \
	mobile-shorebird-release mobile-shorebird-patch mobile-shell mobile-docker-check

POSTMAN_EXPORT := ./scripts/postman-export.py
POSTMAN_VALIDATE := ./scripts/postman-validate.py
POSTMAN_INFO := ./scripts/postman-info.sh
POSTMAN_COLLECTION := postman/APTECH-4-Local.postman_collection.json
POSTMAN_ENVIRONMENT := postman/APTECH-4-Local.postman_environment.json

# Host JDK 21 for seed/run (make seed-demo uses ./mvnw on the host).
# Prefer an already-exported JAVA_HOME (WSL/Linux Temurin, etc.).
# On macOS only, if Homebrew OpenJDK 21 is installed, use it without overriding JAVA_HOME.
HOMEBREW_OPENJDK21_BIN := /opt/homebrew/opt/openjdk@21/bin
HOMEBREW_OPENJDK21_HOME := /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
ifeq ($(shell test -x $(HOMEBREW_OPENJDK21_BIN)/java && echo yes),yes)
  JAVA_HOME ?= $(HOMEBREW_OPENJDK21_HOME)
  export JAVA_HOME
  export PATH := $(HOMEBREW_OPENJDK21_BIN):$(PATH)
endif

help: ## Show available targets
	@awk 'BEGIN {FS = ":.*##"; printf "Aptech-4 Make targets\n\n"} /^[a-zA-Z0-9_-]+:.*?##/ { printf "  %-22s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@printf '\nLocal stack (Compose profiles stack / ai / monitoring):\n'
	@printf '  make deploy-local — core only (API + web + Postgres + Redis)\n'
	@printf '  make deploy-demo-stack — core + Ollama AI + Prometheus/Grafana/exporters\n'
	@printf '  Web: http://localhost:$(WEB_PORT)  (same-origin /api/v1 → Spring)\n'
	@printf '  make up-all — complete demo: deploy-demo-stack + model + seed + smokes\n'
	@printf '  make down — stop stack+ai+monitoring (volumes kept)\n'
	@printf '  make down-all CONFIRM=yes — DELETE all demo named volumes\n'
	@printf '\nAI / monitoring:\n'
	@printf '  Chat health via JWT: GET /api/v1/chat/health — docs/AI_ASSISTANT.md\n'
	@printf '  Grafana http://127.0.0.1:$${GRAFANA_PORT:-3001}  Prometheus http://127.0.0.1:$${PROMETHEUS_PORT:-9090}\n'
	@printf '  make ollama-ensure-model / monitoring-validate-demo / smoke-chat\n'
	@printf '\nDemo seed/reset (Compose Postgres; never auto on long-running API):\n'
	@printf '  make seed-check → seed-demo → smoke-demo\n'
	@printf '  make reset-demo-dry ; make reset-demo CONFIRM=yes\n'
	@printf '  One-shot seed uses SERVER_PORT=$(SEED_SERVER_PORT) so stack API on :8080 can stay up\n'
	@printf '  Full rebuild: make down-all CONFIRM=yes && make up-all  (see docs/DEMO_ENVIRONMENT.md)\n'
	@printf '\nPostman (local API):\n'
	@printf '  make postman-export / postman-validate / postman-info / postman-help\n'
	@printf '  make postman-smoke — optional Newman folder 00-Smoke (requires npx)\n'
	@printf '\nEnv / Flutter notes:\n'
	@printf '  make setup / check-env — .env bootstrap + secret presence (values never printed)\n'
	@printf '  Preferred Flutter: make mobile-build-image && make mobile-doctor\n'
	@printf '  APK/AAB (Docker): make mobile-build-apk|mobile-build-release-apk|mobile-build-release-aab API_BASE_URL=...\n'
	@printf '  API_BASE_URL is build-time (--dart-define), not a runtime OS env inside the app\n'
	@printf '  Shorebird: make mobile-shorebird-auth / mobile-shorebird-workflow; publish needs CONFIRM=yes\n'
	@printf '  Device/emulator E2E deferred until backend is on EC2 (not part of local mobile build)\n'

env-check: ## Ensure .env exists (copy from .env.example ONLY if missing; never overwrite)
	@if [[ ! -f "$(ENV_FILE)" ]]; then \
		cp .env.example "$(ENV_FILE)"; \
		echo "Created $(ENV_FILE) from .env.example — set real secrets locally (never commit)."; \
	else \
		echo "$(ENV_FILE) present (not overwritten)."; \
	fi

up-deps: env-check ## Start Postgres + Redis (preserves volumes)
	$(COMPOSE) up -d postgres redis
	@$(MAKE) --no-print-directory health-deps

down-deps: ## Stop dependency containers (keeps named volumes)
	$(COMPOSE) stop postgres redis || true
	$(COMPOSE) rm -f postgres redis || true
	@echo "Stopped deps. Volumes preserved (aptech4_pgdata, aptech4_redisdata)."

build-stack: env-check ## Build API + web images (Compose profile stack)
	WEB_PORT="$(WEB_PORT)" $(COMPOSE_STACK) build api web

deploy-local: env-check ## Start core local stack only (API+web+Postgres+Redis); keeps volumes
	WEB_PORT="$(WEB_PORT)" $(COMPOSE_STACK) up -d --build
	@$(MAKE) --no-print-directory health-local

# Complete demo stack: core + AI (Ollama) + monitoring. Does not pull models or seed DB.
deploy-demo-stack: env-check ## Start stack+ai+monitoring with AI chat enabled; keeps volumes
	@set -a; [[ -f "$(ENV_FILE)" ]] && source "./$(ENV_FILE)"; set +a; \
	APTECH_AI_ENABLED=true \
	SPRING_AI_MODEL_CHAT=ollama \
	OLLAMA_BASE_URL=http://ollama:11434 \
	SPRING_PROFILES_ACTIVE=local,ai \
	WEB_PORT="$(WEB_PORT)" $(COMPOSE_DEMO) up -d --build
	@$(MAKE) --no-print-directory health-demo-stack
	@echo "Demo stack up (core + AI + monitoring). Model: make ollama-ensure-model"

# Optional AI profile: Ollama + API with Spring AI enabled. Does NOT pull a model automatically.
up-ai: env-check ## Start Ollama only (profile ai); preserves volumes
	$(COMPOSE) --profile ai up -d ollama
	@echo "Ollama up on 127.0.0.1:11434. Pull a model: make ollama-pull MODEL=llama3.2"

deploy-local-ai: env-check ## Core stack + Ollama (no monitoring); enables chat against DNS `ollama`
	APTECH_AI_ENABLED=true \
	SPRING_AI_MODEL_CHAT=ollama \
	OLLAMA_BASE_URL=http://ollama:11434 \
	SPRING_PROFILES_ACTIVE=local,ai \
	WEB_PORT="$(WEB_PORT)" $(COMPOSE) --profile stack --profile ai up -d --build
	@$(MAKE) --no-print-directory health-local
	@echo "AI stack up. Pull model if needed: make ollama-ensure-model"

ollama-pull: ## Pull Ollama model into the ai volume (does not wipe DB)
	@set -a; [[ -f "$(ENV_FILE)" ]] && source "./$(ENV_FILE)"; set +a; \
	model="$(or $(MODEL),$${APTECH_AI_MODEL:-llama3.2})"; \
	echo "Pulling $$model (may take several minutes / GBs)..."; \
	$(COMPOSE) --profile ai exec -T ollama ollama pull "$$model"

ollama-ensure-model: ## Fail if Ollama model missing after pull attempt (required for usable chat)
	@set -euo pipefail; \
	set -a; [[ -f "$(ENV_FILE)" ]] && source "./$(ENV_FILE)"; set +a; \
	model="$(or $(MODEL),$${APTECH_AI_MODEL:-llama3.2})"; \
	echo "Ensuring Ollama is up and model '$$model' is present…"; \
	$(COMPOSE) --profile ai up -d ollama; \
	for i in $$(seq 1 40); do \
		if $(COMPOSE) --profile ai ps --format json ollama 2>/dev/null | grep -q '"Health":"healthy"'; then break; fi; \
		if [[ $$i -eq 40 ]]; then echo "ERROR: ollama not healthy in time"; $(COMPOSE) --profile ai ps; exit 1; fi; \
		sleep 3; \
	done; \
	if $(COMPOSE) --profile ai exec -T ollama ollama show "$$model" >/dev/null 2>&1; then \
		echo "OK: model $$model already present"; \
	else \
		echo "Model $$model missing — pulling (may take several minutes / GBs; failure is fatal)…"; \
		$(COMPOSE) --profile ai exec -T ollama ollama pull "$$model" \
			|| { echo "ERROR: ollama pull failed for $$model — chat demo cannot proceed"; exit 1; }; \
	fi; \
	$(COMPOSE) --profile ai exec -T ollama ollama show "$$model" >/dev/null \
		|| { echo "ERROR: model $$model still missing after pull"; exit 1; }; \
	echo "OK: ollama model $$model ready"

ollama-health: ## Check Ollama HTTP health on loopback
	@curl -fsS http://127.0.0.1:11434/api/tags | head -c 400; echo

down-ai: ## Stop Ollama only; keeps ollama + DB volumes
	$(COMPOSE) stop ollama || true
	$(COMPOSE) rm -f ollama || true
	@echo "Stopped ollama. Volumes preserved (including aptech4_ollama)."

down-local: ## Stop api + web (and optionally leave deps); never removes volumes
	$(COMPOSE) stop api web || true
	$(COMPOSE) rm -f api web || true
	@echo "Stopped api/web. Volumes preserved (aptech4_pgdata, aptech4_redisdata)."

down: ## Stop stack+ai+monitoring services; never removes volumes
	$(COMPOSE_DEMO) stop || true
	@echo "Stopped stack+ai+monitoring. Volumes preserved (pg/redis/ollama/prometheus/grafana)."

down-all: ## DESTRUCTIVE: stop all Compose profiles and delete named volumes (CONFIRM=yes)
	@if [[ "$(CONFIRM)" != "yes" ]]; then \
		echo "Refuse: make down-all CONFIRM=yes is required."; \
		echo "This deletes aptech4_pgdata, aptech4_redisdata, aptech4_ollama, aptech4_prometheus, aptech4_grafana."; \
		echo "Ordinary 'make down' / 'make down-local' never remove volumes."; \
		exit 1; \
	fi
	@echo "Stopping all Compose services and removing named volumes…"
	$(COMPOSE_DEMO) down --volumes || $(COMPOSE) down --volumes
	@echo "down-all finished. Named volumes removed. Next: make up-all"

up-all: deploy-demo-stack ollama-ensure-model seed-demo smoke-demo smoke-chat monitoring-validate-demo ## Complete local demo stack + seed + smokes (volumes kept)
	@echo "up-all finished."
	@echo "  Web:        http://localhost:$(WEB_PORT)"
	@echo "  Grafana:    http://127.0.0.1:$${GRAFANA_PORT:-3001}"
	@echo "  Prometheus: http://127.0.0.1:$${PROMETHEUS_PORT:-9090}"
	@echo "  Ollama:     http://127.0.0.1:11434"
	@echo "See docs/DEMO_ENVIRONMENT.md"

clean: ## Clean Maven target/ and Flutter build artifacts (never DB/volumes/.env)
	cd backend && ./mvnw -q clean
	@echo "Maven target/ cleaned. Database and Docker volumes untouched."
	@$(DEV_FLUTTER) clean-mobile

ps: ## Show compose service status (stack+ai+monitoring)
	$(COMPOSE_DEMO) ps -a

logs-deps: ## Tail Postgres + Redis logs
	$(COMPOSE) logs -f --tail=100 postgres redis

logs-local: ## Tail api + web logs (full stack)
	$(COMPOSE_STACK) logs -f --tail=100 api web

health-deps: ## Wait until Postgres and Redis are healthy
	@echo "Waiting for dependency healthchecks..."
	@for i in $$(seq 1 30); do \
		pg=$$($(COMPOSE) ps --format json postgres 2>/dev/null | grep -c '"Health":"healthy"' || true); \
		rd=$$($(COMPOSE) ps --format json redis 2>/dev/null | grep -c '"Health":"healthy"' || true); \
		if [[ "$$pg" -ge 1 && "$$rd" -ge 1 ]]; then \
			echo "OK: postgres and redis are healthy."; \
			$(COMPOSE) ps; \
			exit 0; \
		fi; \
		sleep 2; \
	done; \
	echo "ERROR: deps not healthy in time."; \
	$(COMPOSE) ps; \
	exit 1

health-local: ## Wait until api + web containers are healthy
	@echo "Waiting for api + web healthchecks..."
	@for i in $$(seq 1 60); do \
		api=$$($(COMPOSE_STACK) ps --format json api 2>/dev/null | grep -c '"Health":"healthy"' || true); \
		web=$$($(COMPOSE_STACK) ps --format json web 2>/dev/null | grep -c '"Health":"healthy"' || true); \
		if [[ "$$api" -ge 1 && "$$web" -ge 1 ]]; then \
			echo "OK: api and web are healthy."; \
			$(COMPOSE_STACK) ps; \
			exit 0; \
		fi; \
		sleep 3; \
	done; \
	echo "ERROR: api/web not healthy in time."; \
	$(COMPOSE_STACK) ps; \
	$(COMPOSE_STACK) logs --tail=80 api web || true; \
	exit 1

health-demo-stack: ## Wait until core + ollama + prometheus + grafana are healthy
	@echo "Waiting for demo stack healthchecks (api/web/ollama/prometheus/grafana)..."
	@for i in $$(seq 1 90); do \
		api=$$($(COMPOSE_DEMO) ps --format json api 2>/dev/null | grep -c '"Health":"healthy"' || true); \
		web=$$($(COMPOSE_DEMO) ps --format json web 2>/dev/null | grep -c '"Health":"healthy"' || true); \
		ollama=$$($(COMPOSE_DEMO) ps --format json ollama 2>/dev/null | grep -c '"Health":"healthy"' || true); \
		prom=$$($(COMPOSE_DEMO) ps --format json prometheus 2>/dev/null | grep -c '"Health":"healthy"' || true); \
		graf=$$($(COMPOSE_DEMO) ps --format json grafana 2>/dev/null | grep -c '"Health":"healthy"' || true); \
		if [[ "$$api" -ge 1 && "$$web" -ge 1 && "$$ollama" -ge 1 && "$$prom" -ge 1 && "$$graf" -ge 1 ]]; then \
			echo "OK: api, web, ollama, prometheus, grafana are healthy."; \
			$(COMPOSE_DEMO) ps; \
			exit 0; \
		fi; \
		sleep 3; \
	done; \
	echo "ERROR: demo stack not healthy in time (api=$$api web=$$web ollama=$$ollama prom=$$prom graf=$$graf)."; \
	$(COMPOSE_DEMO) ps; \
	$(COMPOSE_DEMO) logs --tail=60 api web ollama prometheus grafana || true; \
	exit 1

monitoring-up: env-check ## Start Prometheus/Grafana/exporters (profile monitoring); starts Postgres+Redis
	@$(COMPOSE) up -d postgres redis
	@$(COMPOSE_MONITORING) up -d
	@echo "Monitoring up (volumes aptech4_prometheus / aptech4_grafana preserved)."
	@echo "Grafana:    http://127.0.0.1:$${GRAFANA_PORT:-3001}  (user/pass from .env GRAFANA_*)"
	@echo "Prometheus: http://127.0.0.1:$${PROMETHEUS_PORT:-9090}"
	@echo "API scrape needs profile stack (make deploy-local / up-all). Docs: docs/MONITORING.md"

monitoring-down: ## Stop monitoring services only; never removes volumes or app data
	@$(COMPOSE_MONITORING) stop prometheus grafana postgres-exporter redis-exporter cadvisor || true
	@$(COMPOSE_MONITORING) rm -f prometheus grafana postgres-exporter redis-exporter cadvisor || true
	@echo "Stopped monitoring profile. Volumes aptech4_prometheus / aptech4_grafana kept."

monitoring-status: ## Show monitoring + scrape-related container status
	@$(COMPOSE) ps postgres redis api ollama 2>/dev/null || true
	@$(COMPOSE_MONITORING) ps prometheus grafana postgres-exporter redis-exporter cadvisor 2>/dev/null || true

monitoring-validate: ## Check Prometheus/Grafana readiness (does not require api target up)
	@set -e; \
	set -a; [[ -f "$(ENV_FILE)" ]] && source "./$(ENV_FILE)"; set +a; \
	prom=http://127.0.0.1:$${PROMETHEUS_PORT:-9090}; \
	graf=http://127.0.0.1:$${GRAFANA_PORT:-3001}; \
	echo "GET $$prom/-/ready"; \
	curl -fsS "$$prom/-/ready" | tee /tmp/aptech4-prom-ready.txt; echo; \
	echo "GET $$prom/api/v1/targets"; \
	curl -fsS "$$prom/api/v1/targets" | python3 -c 'import sys,json; d=json.load(sys.stdin); \
print("targets", [(t["labels"].get("job"), t["health"]) for t in d.get("data",{}).get("activeTargets",[])])'; \
	echo "GET $$graf/api/health"; \
	curl -fsS "$$graf/api/health" | tee /tmp/aptech4-grafana-health.json; echo; \
	echo "OK: monitoring-validate basic checks passed (inspect target health above)."

monitoring-validate-demo: ## Require Prometheus/Grafana ready and critical scrape targets up (incl. aptech4-api)
	@bash ./scripts/monitoring-validate-demo.sh

test-nginx-assets-no-spa-fallback: ## Offline: nginx /assets must 404 (never SPA HTML fallback)
	@bash ./scripts/test-nginx-assets-no-spa-fallback.sh

smoke-local: ## HTTP smoke: web UI + public API via same-origin nginx proxy
	@base="http://127.0.0.1:$(WEB_PORT)"; \
	echo "GET $$base/"; \
	code=$$(curl -sS -D /tmp/aptech4-web-smoke.hdr -o /tmp/aptech4-web-smoke.html -w '%{http_code}' "$$base/"); \
	test "$$code" = "200" || { echo "ERROR: web HTTP $$code"; exit 1; }; \
	echo "OK: web $$code"; \
	echo "GET $$base/api/v1/health"; \
	curl -sS "$$base/api/v1/health" | tee /tmp/aptech4-api-smoke.json; \
	grep -q '"status":"OK"' /tmp/aptech4-api-smoke.json || { echo "ERROR: health body unexpected"; exit 1; }; \
	echo ""; echo "GET $$base/api/v1/categories"; \
	code=$$(curl -sS -o /tmp/aptech4-cat-smoke.json -w '%{http_code}' "$$base/api/v1/categories"); \
	test "$$code" = "200" || { echo "ERROR: categories HTTP $$code"; exit 1; }; \
	echo "OK: categories $$code (same-origin /api routing)"; \
	echo "GET $$base/assets/missing-chunk-regression-$$RANDOM.js (expect 404, not HTML)"; \
	code=$$(curl -sS -D /tmp/aptech4-missing-asset.hdr -o /tmp/aptech4-missing-asset.body -w '%{http_code}' \
		"$$base/assets/ChatPanel-stale-hash-does-not-exist.js"); \
	test "$$code" = "404" || { echo "ERROR: missing asset expected 404, got $$code (SPA fallback serving HTML as JS?)"; exit 1; }; \
	! grep -qi '<!doctype html' /tmp/aptech4-missing-asset.body || { echo "ERROR: missing asset body looks like HTML"; exit 1; }; \
	echo "OK: missing /assets/* returns 404 (not index.html)"

reset-deps-volumes: ## DESTRUCTIVE: stop deps and delete Postgres/Redis volumes
	@echo "WARNING: This deletes aptech4_pgdata and aptech4_redisdata."
	@read -r -p "Type 'yes' to continue: " ans; \
	if [[ "$$ans" != "yes" ]]; then echo "Aborted."; exit 1; fi
	$(COMPOSE) down postgres redis --volumes || $(COMPOSE) down --volumes
	@echo "Volumes removed."

seed-check: env-check ## Seed preflight / dry report (no writes); refuse prod profile
	@set -a; [[ -f "$(ENV_FILE)" ]] && source "./$(ENV_FILE)"; set +a; \
	host="$${POSTGRES_HOST:-localhost}"; \
	port="$${POSTGRES_PORT:-5432}"; \
	db="$${POSTGRES_DB:-aptech4}"; \
	admin="$${DEMO_ADMIN_EMAIL:-admin@aptech4.local}"; \
	customer="$${DEMO_CUSTOMER_EMAIL:-customer@aptech4.local}"; \
	echo "Seed target database: $$host:$$port/$$db"; \
	echo "Spring profiles: $${SPRING_PROFILES_ACTIVE:-local}"; \
	echo "One-shot seed/reset bind port: $(SEED_SERVER_PORT) (stack API may keep :8080)"; \
	echo "APTECH_SEED_ENABLED default for long-running api container: false (deploy-local does not seed)"; \
	if echo "$${SPRING_PROFILES_ACTIVE:-}" | grep -Eq '(^|,)prod($$|,)'; then \
		echo "ERROR: SPRING_PROFILES_ACTIVE includes 'prod' — refuse seed."; \
		exit 1; \
	fi; \
	if [[ -z "$${DEMO_ADMIN_PASSWORD:-}" || -z "$${DEMO_CUSTOMER_PASSWORD:-}" ]]; then \
		echo "ERROR: DEMO_ADMIN_PASSWORD and DEMO_CUSTOMER_PASSWORD must be set in .env"; \
		exit 1; \
	fi; \
	if [[ -z "$${JWT_ACCESS_SECRET:-}" || -z "$${JWT_REFRESH_SECRET:-}" ]]; then \
		echo "ERROR: JWT_ACCESS_SECRET and JWT_REFRESH_SECRET must be set in .env"; \
		exit 1; \
	fi; \
	if $(COMPOSE) ps postgres 2>/dev/null | grep -Eq 'running|healthy|Up'; then \
		echo "Read-only demo natural-key counts (no writes):"; \
		$(COMPOSE) exec -T postgres psql -U "$${POSTGRES_USER:-aptech}" -d "$$db" -v ON_ERROR_STOP=1 \
			-c "SELECT (SELECT count(*) FROM users WHERE email IN ('$$admin', '$$customer')) AS demo_users, \
			(SELECT count(*) FROM categories WHERE slug IN ('demo-electronics','demo-books','demo-home','demo-archive')) AS demo_categories, \
			(SELECT count(*) FROM products WHERE sku LIKE 'DEMO-%') AS demo_products;"; \
	else \
		echo "Postgres not running yet — counts skipped (make up-deps / deploy-local first)."; \
	fi; \
	echo "OK: seed-check passed (dry / no mutations)."

invalidate-catalog-cache: ## Clear Redis catalog:* keys after external seed/reset (not FLUSHALL)
	@if $(COMPOSE) ps --status running redis 2>/dev/null | grep -q redis; then \
		n=$$($(COMPOSE) exec -T redis sh -c 'redis-cli --scan --pattern "catalog:*" | wc -l' | tr -d '[:space:]'); \
		$(COMPOSE) exec -T redis sh -c 'redis-cli --scan --pattern "catalog:*" | while IFS= read -r k; do [ -n "$$k" ] && redis-cli DEL "$$k" >/dev/null; done'; true; \
		echo "OK: cleared $$n catalog cache key(s) (running API sees fresh demo catalog)."; \
	else \
		echo "Redis not running — skip catalog cache invalidate."; \
	fi

seed-demo: seed-check up-deps ## Idempotent demo seed into Compose Postgres (does not wipe volumes)
	@set -a; . "./$(ENV_FILE)"; set +a; \
	echo "Running one-shot seed (APTECH_SEED_ENABLED=true, exit-after=true, SERVER_PORT=$(SEED_SERVER_PORT))…"; \
	cd backend && \
	APTECH_SEED_ENABLED=true \
	APTECH_SEED_EXIT_AFTER=true \
	SERVER_PORT="$(SEED_SERVER_PORT)" \
	POSTGRES_HOST="$${POSTGRES_HOST:-localhost}" \
	POSTGRES_PORT="$${POSTGRES_PORT:-5432}" \
	POSTGRES_DB="$${POSTGRES_DB:-aptech4}" \
	POSTGRES_USER="$${POSTGRES_USER:-aptech}" \
	POSTGRES_PASSWORD="$${POSTGRES_PASSWORD}" \
	REDIS_HOST="$${REDIS_HOST:-localhost}" \
	REDIS_PORT="$${REDIS_PORT:-6379}" \
	JWT_ACCESS_SECRET="$${JWT_ACCESS_SECRET}" \
	JWT_REFRESH_SECRET="$${JWT_REFRESH_SECRET}" \
	DEMO_ADMIN_EMAIL="$${DEMO_ADMIN_EMAIL:-admin@aptech4.local}" \
	DEMO_ADMIN_PASSWORD="$${DEMO_ADMIN_PASSWORD}" \
	DEMO_CUSTOMER_EMAIL="$${DEMO_CUSTOMER_EMAIL:-customer@aptech4.local}" \
	DEMO_CUSTOMER_PASSWORD="$${DEMO_CUSTOMER_PASSWORD}" \
	SPRING_PROFILES_ACTIVE="$${SPRING_PROFILES_ACTIVE:-local}" \
	./mvnw -q spring-boot:run
	@$(MAKE) --no-print-directory invalidate-catalog-cache
	@echo "Demo seed finished. Long-running api keeps APTECH_SEED_ENABLED=false."

reset-demo-dry: seed-check up-deps ## Dry-run: print demo reset scope; no deletes
	@set -a; . "./$(ENV_FILE)"; set +a; \
	echo "Dry-run demo reset (APTECH_SEED_RESET=true, RESET_DRY_RUN=true, SERVER_PORT=$(SEED_SERVER_PORT))…"; \
	echo "Target: $${POSTGRES_HOST:-localhost}:$${POSTGRES_PORT:-5432}/$${POSTGRES_DB:-aptech4}"; \
	echo "Scope: configured DEMO_* emails + DemoSeedCatalog slugs/SKUs only (not full DB)."; \
	cd backend && \
	APTECH_SEED_RESET=true \
	APTECH_SEED_RESET_DRY_RUN=true \
	APTECH_SEED_EXIT_AFTER=true \
	SERVER_PORT="$(SEED_SERVER_PORT)" \
	POSTGRES_HOST="$${POSTGRES_HOST:-localhost}" \
	POSTGRES_PORT="$${POSTGRES_PORT:-5432}" \
	POSTGRES_DB="$${POSTGRES_DB:-aptech4}" \
	POSTGRES_USER="$${POSTGRES_USER:-aptech}" \
	POSTGRES_PASSWORD="$${POSTGRES_PASSWORD}" \
	REDIS_HOST="$${REDIS_HOST:-localhost}" \
	REDIS_PORT="$${REDIS_PORT:-6379}" \
	JWT_ACCESS_SECRET="$${JWT_ACCESS_SECRET}" \
	JWT_REFRESH_SECRET="$${JWT_REFRESH_SECRET}" \
	DEMO_ADMIN_EMAIL="$${DEMO_ADMIN_EMAIL:-admin@aptech4.local}" \
	DEMO_ADMIN_PASSWORD="$${DEMO_ADMIN_PASSWORD}" \
	DEMO_CUSTOMER_EMAIL="$${DEMO_CUSTOMER_EMAIL:-customer@aptech4.local}" \
	DEMO_CUSTOMER_PASSWORD="$${DEMO_CUSTOMER_PASSWORD}" \
	SPRING_PROFILES_ACTIVE="$${SPRING_PROFILES_ACTIVE:-local}" \
	./mvnw -q spring-boot:run
	@echo "Dry-run finished — database unchanged."

reset-demo: seed-check ## Scoped demo reset + reseed (CONFIRM=yes required; not volume wipe)
	@if [[ "$(CONFIRM)" != "yes" ]]; then \
		echo "ERROR: destructive reset requires CONFIRM=yes"; \
		echo "  make reset-demo-dry     # inspect scope, no deletes"; \
		echo "  make reset-demo CONFIRM=yes"; \
		echo "This is NOT a database/volume reset. Use reset-deps-volumes only if you intend to wipe volumes."; \
		exit 1; \
	fi
	@$(MAKE) --no-print-directory up-deps
	@set -a; . "./$(ENV_FILE)"; set +a; \
	echo "Confirmed demo reset + reseed (SERVER_PORT=$(SEED_SERVER_PORT))…"; \
	echo "Target: $${POSTGRES_HOST:-localhost}:$${POSTGRES_PORT:-5432}/$${POSTGRES_DB:-aptech4}"; \
	cd backend && \
	APTECH_SEED_RESET=true \
	APTECH_SEED_RESET_DRY_RUN=false \
	APTECH_SEED_RESET_CONFIRM=yes \
	APTECH_SEED_RESEED_AFTER_RESET=true \
	APTECH_SEED_EXIT_AFTER=true \
	SERVER_PORT="$(SEED_SERVER_PORT)" \
	POSTGRES_HOST="$${POSTGRES_HOST:-localhost}" \
	POSTGRES_PORT="$${POSTGRES_PORT:-5432}" \
	POSTGRES_DB="$${POSTGRES_DB:-aptech4}" \
	POSTGRES_USER="$${POSTGRES_USER:-aptech}" \
	POSTGRES_PASSWORD="$${POSTGRES_PASSWORD}" \
	REDIS_HOST="$${REDIS_HOST:-localhost}" \
	REDIS_PORT="$${REDIS_PORT:-6379}" \
	JWT_ACCESS_SECRET="$${JWT_ACCESS_SECRET}" \
	JWT_REFRESH_SECRET="$${JWT_REFRESH_SECRET}" \
	DEMO_ADMIN_EMAIL="$${DEMO_ADMIN_EMAIL:-admin@aptech4.local}" \
	DEMO_ADMIN_PASSWORD="$${DEMO_ADMIN_PASSWORD}" \
	DEMO_CUSTOMER_EMAIL="$${DEMO_CUSTOMER_EMAIL:-customer@aptech4.local}" \
	DEMO_CUSTOMER_PASSWORD="$${DEMO_CUSTOMER_PASSWORD}" \
	SPRING_PROFILES_ACTIVE="$${SPRING_PROFILES_ACTIVE:-local}" \
	./mvnw -q spring-boot:run
	@$(MAKE) --no-print-directory invalidate-catalog-cache
	@echo "Demo reset + reseed finished. Non-demo rows and Docker volumes were not wiped."

smoke-demo: ## After seed: login + public catalog via stack nginx (WEB_PORT)
	@set -a; [[ -f "$(ENV_FILE)" ]] && source "./$(ENV_FILE)"; set +a; \
	base="http://127.0.0.1:$(WEB_PORT)"; \
	email="$${DEMO_CUSTOMER_EMAIL:-customer@aptech4.local}"; \
	pass="$${DEMO_CUSTOMER_PASSWORD:?DEMO_CUSTOMER_PASSWORD required}"; \
	echo "POST $$base/api/v1/auth/login (email=$$email; password not printed)"; \
	code=$$(curl -sS -o /tmp/aptech4-demo-login.json -w '%{http_code}' \
		-X POST "$$base/api/v1/auth/login" \
		-H 'Content-Type: application/json' \
		-d "{\"email\":\"$$email\",\"password\":\"$$pass\"}"); \
	test "$$code" = "200" || { echo "ERROR: login HTTP $$code"; cat /tmp/aptech4-demo-login.json; exit 1; }; \
	grep -q accessToken /tmp/aptech4-demo-login.json || { echo "ERROR: missing accessToken"; exit 1; }; \
	echo "OK: login $$code"; \
	echo "GET $$base/api/v1/products?size=5"; \
	code=$$(curl -sS -o /tmp/aptech4-demo-products.json -w '%{http_code}' "$$base/api/v1/products?size=5"); \
	test "$$code" = "200" || { echo "ERROR: products HTTP $$code"; exit 1; }; \
	grep -q 'FOOD-' /tmp/aptech4-demo-products.json || { echo "ERROR: expected FOOD-* SKUs in catalog"; exit 1; }; \
	! grep -q 'DEMO-' /tmp/aptech4-demo-products.json || { echo "ERROR: legacy DEMO-* SKUs must not appear after FoodMart-only seed"; exit 1; }; \
	echo "OK: products $$code (FoodMart SKUs present)"

smoke-chat: ## Require AI chat READY + one authenticated completion (fail if AI omitted)
	@WEB_PORT="$(WEB_PORT)" bash ./scripts/smoke-chat.sh

smoke-product-image-upload: ## Opt-in: Admin image upload via nginx (needs stack + DEMO_ADMIN_*)
	@bash ./scripts/smoke-product-image-upload.sh

test-product-image-upload: ## Host unit/IT + frontend suites for product image upload
	@echo "== backend ProductImage* + AdminProductImageUpload =="
	@cd backend && ./mvnw -Dtest=ProductImageUploadServiceTest,ProductImagePathRulesTest,AdminProductImageUploadIntegrationTest test
	@echo "== frontend admin products + resolve-product-image + cart =="
	@cd frontend && npm test -- --run \
		src/features/admin/products \
		src/features/catalog/lib/resolve-product-image.test.ts \
		src/features/cart/pages/CartPage.test.tsx
	@echo "OK: test-product-image-upload (automated). Optional live stack: make smoke-product-image-upload"

check-up-all-makefile: ## Static check that up-all wires core+AI+monitoring (no Docker)
	@bash ./scripts/check-up-all-makefile.sh

test-monitoring-validate-demo: ## Offline: hard targets fail; cAdvisor-down warns only
	@bash ./scripts/test-monitoring-validate-demo.sh

# ---------------------------------------------------------------------------
# Postman (local Compose API via Nginx :8088)
# ---------------------------------------------------------------------------

postman-export: ## Generate Postman collection + environment JSON (placeholders for passwords)
	@python3 "$(POSTMAN_EXPORT)"
	@$(MAKE) --no-print-directory postman-validate

postman-validate: ## Validate Postman JSON schema, required requests, baseUrl, no committed tokens
	@python3 "$(POSTMAN_VALIDATE)"

postman-info: ## Print local URLs, documented demo accounts, Postman paths (not .env secrets)
	@chmod +x "$(POSTMAN_INFO)" 2>/dev/null || true
	@"$(POSTMAN_INFO)"

postman-help: ## How to import collection/environment and run requests
	@printf '%s\n' \
		'Postman local workflow' \
		'----------------------' \
		'1. make deploy-local && make seed-demo' \
		'2. make postman-export' \
		'3. Import postman/*.json into Postman; select env \"APTECH-4 Local\"' \
		'4. Set customerPassword / adminPassword from: make postman-info' \
		'5. Run folder 00-Smoke (safe), then Customer/Admin as needed' \
		'6. baseUrl must stay http://localhost:$(WEB_PORT)/api/v1' \
		'7. Do not commit exports containing live JWTs' \
		'Details: postman/README.md'

postman-smoke: ## Optional Newman smoke (folder 00-Smoke); skips if npx/newman unavailable
	@$(MAKE) --no-print-directory postman-validate
	@# Prefer nvm node if present (sandbox/CI shells often lack npx on PATH).
	@export PATH="$$HOME/.nvm/versions/node/$$(ls "$$HOME/.nvm/versions/node" 2>/dev/null | tail -1)/bin:$${PATH}"; \
	if [[ -d /opt/homebrew/bin ]]; then export PATH="/opt/homebrew/bin:$$PATH"; fi; \
	if ! command -v npx >/dev/null 2>&1; then \
		echo "SKIP: npx not found — install Node or run requests manually in Postman."; \
	else \
		echo "Running Newman folder \"00-Smoke (safe)\" against http://localhost:$(WEB_PORT)/api/v1 …"; \
		npx --yes newman@6 run "$(POSTMAN_COLLECTION)" \
			-e "$(POSTMAN_ENVIRONMENT)" \
			--folder "00-Smoke (safe)" \
			--env-var "customerPassword=customer" \
			--env-var "adminPassword=admin" \
			--env-var "baseUrl=http://localhost:$(WEB_PORT)/api/v1" \
			--env-var "webOrigin=http://localhost:$(WEB_PORT)" \
			--timeout-request 15000 \
			--color on; \
	fi

# ---------------------------------------------------------------------------
# Flutter / FVM (Android customer app under mobile/)
# ---------------------------------------------------------------------------

check-tools: ## Read-only: host tools (make/git/brew/fvm/java) — no installs
	@$(DEV_FLUTTER) check-tools

check-env: ## Verify required .env keys exist (values never printed)
	@$(CHECK_DOTENV)

check-env-flutter: ## Read-only: FVM pin, Flutter project, Android SDK hints
	@$(DEV_FLUTTER) check-env

status: ## Summarize tool availability, pin, and mobile project readiness
	@$(DEV_FLUTTER) status

install-tools: ## Install FVM (+ pinned Flutter via FVM). Requires CONFIRM=yes
	@$(DEV_FLUTTER) install-tools "$(CONFIRM)"

setup: env-check ## Ensure .env exists (no overwrite) + optional host FVM prepare
	@echo "OK: setup — .env ready. Set SHOREBIRD_TOKEN locally for Shorebird OTA."
	@echo "Optional host Flutter: $(DEV_FLUTTER) setup (needs FVM + mobile/.fvmrc)."
	@if command -v fvm >/dev/null 2>&1 && [[ -f mobile/pubspec.yaml ]]; then \
		$(DEV_FLUTTER) setup; \
	else \
		echo "Skipping host FVM setup (no fvm and/or no mobile/pubspec.yaml). Use make mobile-* instead."; \
	fi

deps: ## flutter pub get via FVM (no pub upgrade)
	@$(DEV_FLUTTER) deps

get: deps ## Alias for deps

doctor: ## fvm flutter doctor -v
	@$(DEV_FLUTTER) doctor

analyze: ## fvm flutter analyze (requires mobile/pubspec.yaml)
	@$(DEV_FLUTTER) analyze

test: ## fvm flutter test (requires mobile/pubspec.yaml)
	@$(DEV_FLUTTER) test

run: check-env ## Run Flutter via FVM with env loaded (DEVICE= / API_BASE_URL= optional)
	@API_BASE_URL="$(API_BASE_URL)" $(DEV_FLUTTER) run "$(DEVICE)"

clean-mobile: ## fvm flutter clean in mobile/ only
	@$(DEV_FLUTTER) clean-mobile

# ---------------------------------------------------------------------------
# Flutter + Shorebird via Docker (no host Flutter required)
# ---------------------------------------------------------------------------

mobile-docker-check: ## Verify Docker daemon is reachable (read-only)
	@$(MOBILE_DOCKER) ensure-docker

mobile-build-image: ## Build pinned Flutter+Android+Shorebird image
	@$(MOBILE_DOCKER) build-image

mobile-versions: ## flutter/dart/shorebird/java versions inside container
	@$(MOBILE_DOCKER) versions

mobile-doctor: ## flutter doctor -v + shorebird doctor inside container
	@$(MOBILE_DOCKER) doctor

mobile-shorebird-auth: ## Verify Shorebird Cloud auth via CLI (needs SHOREBIRD_TOKEN; never prints it)
	@$(MOBILE_DOCKER) shorebird-auth

mobile-pub-get: ## flutter pub get in Docker (requires mobile/pubspec.yaml)
	@$(MOBILE_DOCKER) pub-get

mobile-analyze: ## flutter analyze in Docker
	@$(MOBILE_DOCKER) analyze

mobile-test: ## flutter test in Docker
	@$(MOBILE_DOCKER) test

mobile-build-android: ## Debug APK via Docker (alias of mobile-build-apk; API_BASE_URL= → dart-define)
	@API_BASE_URL="$(API_BASE_URL)" $(MOBILE_DOCKER) build-android

mobile-build-apk: ## Debug APK via Docker; output mobile/build/app/outputs/flutter-apk/app-debug.apk
	@API_BASE_URL="$(API_BASE_URL)" $(MOBILE_DOCKER) build-apk

mobile-build-release-apk: ## Release APK via Docker (debug signing only; no prod keystore). API_BASE_URL= → dart-define
	@API_BASE_URL="$(API_BASE_URL)" $(MOBILE_DOCKER) build-release-apk

mobile-build-release-aab: ## Release AAB via Docker (debug signing only). Output …/bundle/release/app-release.aab
	@API_BASE_URL="$(API_BASE_URL)" $(MOBILE_DOCKER) build-release-aab

mobile-create: ## flutter create into mobile/ (CONFIRM=yes; no overwrite if pubspec exists)
	@$(MOBILE_DOCKER) create "$(CONFIRM)"

mobile-shorebird-init: ## One-time shorebird init (needs SHOREBIRD_TOKEN; not run in normal build)
	@$(MOBILE_DOCKER) shorebird-init

mobile-shorebird-workflow: ## Read-only: shorebird.yaml + CLI release/patch help (does NOT publish)
	@$(MOBILE_DOCKER) shorebird-workflow

mobile-shorebird-release: ## PUBLISH Shorebird Android release (CONFIRM=yes + SHOREBIRD_TOKEN) — gated
	@CONFIRM="$(CONFIRM)" API_BASE_URL="$(API_BASE_URL)" $(MOBILE_DOCKER) shorebird-release

mobile-shorebird-patch: ## PUBLISH Shorebird OTA patch (CONFIRM=yes + SHOREBIRD_TOKEN) — gated
	@CONFIRM="$(CONFIRM)" API_BASE_URL="$(API_BASE_URL)" $(MOBILE_DOCKER) shorebird-patch

mobile-shell: ## Interactive shell in mobile toolchain container
	@$(MOBILE_DOCKER) shell
