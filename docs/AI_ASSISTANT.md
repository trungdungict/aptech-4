# Local AI Assistant (ADR-019)

Customer and Admin chat assistants backed by **Ollama** + **Spring AI 2.0.0**. The store works with AI disabled.

## Hardware / model

| Item | Guidance |
|------|----------|
| Default model | `llama3.2` (override `APTECH_AI_MODEL`) |
| RAM | Plan **8GB+** free for small models; larger tags need more |
| Download | Manual `make ollama-pull` — never during normal API startup |
| Tool calling | Required for accurate order/inventory answers |

Benchmark candidates on your machine (`ollama run …`) before changing the default. Document measured first-token latency after a local pull.

## Environment

| Variable | Default | Meaning |
|----------|---------|---------|
| `APTECH_AI_ENABLED` | `false` | Master switch |
| `SPRING_AI_MODEL_CHAT` | `none` | Set `ollama` when enabling |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Host Ollama; Compose AI stack uses `http://ollama:11434` |
| `APTECH_AI_MODEL` | `llama3.2` | Chat model name |
| `APTECH_AI_TIMEOUT_SECONDS` | `90` | Bounded inference wait (Compose-wired); Ollama down → 503 |
| `APTECH_AI_LOW_STOCK_THRESHOLD` | `5` | Admin low-stock available threshold |
| `APTECH_AI_MAX_HISTORY` | `12` | Bounded in-memory turns |

## Local commands

```bash
# App without AI (existing)
make deploy-local

# Optional Ollama container only
make up-ai
make ollama-pull MODEL=llama3.2
make ollama-health

# Full stack + AI (API talks to Compose service `ollama`)
make deploy-local-ai

# Complete local demo (core + AI + monitoring + model pull + seed + smokes)
make up-all

# Stop Ollama without touching Postgres/Redis volumes
make down-ai
```

Host-run Spring (Maven) with Docker Ollama:

```bash
export APTECH_AI_ENABLED=true SPRING_AI_MODEL_CHAT=ollama OLLAMA_BASE_URL=http://127.0.0.1:11434
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,ai
```

Do **not** use `localhost` inside the API container to mean the Mac host — use `host.docker.internal` or the `ollama` service name.

## API

Authenticated Bearer JWT.

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/v1/chat/health` | `DISABLED` / `UNAVAILABLE` / `READY` |
| POST | `/api/v1/chat/messages` | JSON `{ message, conversationId?, language? }` → `ChatResponse` |
| POST | `/api/v1/chat/messages/stream` | SSE events: `delta`, `done`, `error` |
| GET | `/api/v1/chat/suggested-prompts` | English defaults for current role |

Nginx: `/api/v1/chat/` has `proxy_buffering off` for SSE.

## Tools

**Customer (identity from JWT only):** `searchProducts`, `getProductDetails`, `getMyOrders`, `getMyOrderDetails`, `getMyLatestOrderSummary`, `getCheckoutHelp`

**Admin (`ROLE_ADMIN` enforced in analytics service):** `getOrderCountsByStatus`, `getOrdersRequiringAttention`, `getSalesSummary`, `getTopSellingProducts`, `getLowStockProducts`, `getOutOfStockProducts`, `getInventoryRiskSummary`, `getReorderRecommendations`, `getCancellationSummary`

**Deterministic Admin short-circuit (`AdminIntentRouter`):** High-confidence Admin prompts bypass the LLM:
- order attention count (`PAID`+`PROCESSING`)
- sales summary (last 30 days; revenue statuses PAID/PROCESSING/SHIPPED/COMPLETED)
- list by status (e.g. paid orders)
- list by total price (min/max VND; `$` without VND asks clarification)
- inventory low/out/risk

Replies are templated from verified repository data (`timing.path` like `deterministic_sales`). Internal method/enum names stay in logs. Tool envelopes distinguish `SUCCESS_NONEMPTY` / `SUCCESS_EMPTY` / `FAILURE`. LLM tool calls run with a preserved SecurityContext so `@PreAuthorize` still applies.

**SSE:** `POST /messages/stream` emits zero or more `delta` events then exactly one terminal `done` or `error`. Auth for Tomcat async dispatch uses `RequestAttributeSecurityContextRepository` (JWT saved onto the request) so completion does not abort chunked encoding. Nginx chat location keeps `proxy_buffering off`.

MVP is **read-only** — no price/stock/order mutations via tools.

## Language

- UI + first greeting default **English**
- Selector: English / Vietnamese (stored in `localStorage`)
- Auto-detect Vietnamese diacritics when no manual preference is sent
- Manual selection overrides detection until changed
- Does not affect authorization

## Security

- No arbitrary SQL/shell/URL tools
- Customer cannot call admin analytics (tools not registered for their ChatClient turn)
- Admin tools re-check `hasRole('ADMIN')`
- Conversations are per-user in memory; logout remounts the widget

## Troubleshooting

| Symptom | Check |
|---------|--------|
| `AI Disabled` 503 | `APTECH_AI_ENABLED=true` and profile `ai` |
| `Model Unavailable` | `ollama list`, `OLLAMA_BASE_URL`, model pulled |
| Slow / OOM | Smaller model; stop other containers; raise host RAM |
| Empty SSE | Rebuild web image (nginx chat location); check proxy buffering |
