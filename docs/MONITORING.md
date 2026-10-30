# Aptech-4 monitoring (Prometheus + Grafana)

Optional Compose profile `monitoring` scrapes the real stack. It does **not** invent sample series and does **not** change `make deploy-local` / `smoke-local` / AI targets.

This is a **demo SRE observability** deliverable: one unified dashboard, no Alertmanager, no incident workflows, no tracing expansion.

## Dashboard overview

| Field | Value |
|-------|-------|
| Name | **Aptech-4 \| SRE System Overview** |
| UID | `aptech4-sre-overview` |
| JSON | `monitoring/grafana/dashboards/aptech4-sre-overview.json` |
| Count | **Exactly one** provisioned dashboard |
| Refresh | 30s |
| Default range | last 1h |

Variables:

- **Datasource** — Grafana datasource variable matching `/^Prometheus-/` (Local: `Prometheus-Local` / `prometheus-local`).
- **Environment** — `Local` \| `Production` (label for demos; metrics are not dual-labeled in one Prometheus yet).
- **Endpoint** — multi-select of `uri` templates (`allValue=/api/v1/.*`).

Generator (optional rebuild): `python3 monitoring/scripts/gen_sre_overview.py`.

## Six dashboard sections

| Row | Purpose |
|-----|---------|
| 1 System Health | Mandatory deps health, API availability (scrape `up`), request rate, HTTP success rate, p95 |
| 2 Traffic & volume | Rate vs count, status distribution/counts, top endpoints |
| 3 Latency & errors | p50/p95/p99, 4xx/5xx rates, error bars, latency heatmap, slowest routes |
| 4 Infra & JVM | API process CPU, JVM heap, Docker VM cAdvisor scope, trends, GC, threads |
| 5 DB & deps | Postgres, Hikari vs server connections, Redis, optional Ollama/chat |
| 6 DEMO SLI/SLO | Availability 99.9%, Latency 95%≤500ms, availability error budget (explicitly DEMO, not a production SLA) |

## Concept distinctions

| Pair | Meaning |
|------|---------|
| **Request rate vs count** | `rate()` ≈ req/s now; `increase([$__range])` ≈ total over the selected window |
| **HTTP 200 vs 2xx** | 200 is one success code; 2xx includes 201 (checkout), 204, etc. |
| **Error rate vs count** | `rate()` of 4xx/5xx vs `increase()` totals over `$__range` |
| **API process CPU vs Docker VM CPU** | `process_cpu_usage` = JVM process; cAdvisor `id="/"` on Docker Desktop Mac = **VM aggregate**, not the API container |
| **JVM heap vs container memory** | Heap is managed Java heap; container/VM working set is OS RSS/cgroup |
| **Hikari vs PostgreSQL connections** | Hikari = API pool; `pg_stat_database_numbackends` = server-side backends for `aptech4` |
| **Availability vs success rate** | Availability ≈ scrape/`up` (process reachable); success rate ≈ fraction of HTTP 2xx |

## Mandatory vs optional components

**Mandatory** (affect Overall System Health): `aptech4-api`, `postgres-exporter`, `redis-exporter`.

**Optional**: Ollama / AI. No Ollama Prometheus job is scraped today → Ollama Health shows **N/A**. Optional down must not mark the system UNAVAILABLE.

## Local setup

```bash
make monitoring-up
make deploy-local          # or make deploy-local-ai
make monitoring-status
make monitoring-validate
```

- Grafana: http://127.0.0.1:3001 (`GRAFANA_ADMIN_*` in `.env`)
- Prometheus: http://127.0.0.1:9090 (loopback only)

Volumes kept on `monitoring-down`: `aptech4_prometheus`, `aptech4_grafana`.

## Production configuration (checklist — not invented)

Production EC2/Linux monitoring is **not configured in this repo** (blocker B-003: no `EC2_HOST`). Do **not** expose Prometheus publicly.

Checklist when ready:

1. Run Prometheus + Grafana on private network / VPN / SG-restricted hosts (same scrape jobs as `monitoring/prometheus/prometheus.yml`, with production DNS names).
2. Provision datasource from `monitoring/grafana/provisioning/datasources/datasource-production.example.yml` → name `Prometheus-Production`, uid `prometheus-production`, private URL only.
3. Mount the **same** `aptech4-sre-overview.json` (same UID).
4. Set Grafana datasource variable to `Prometheus-Production`.
5. On Linux Docker, cAdvisor often exposes per-container `name` labels; Mac Docker Desktop typically does not.
6. Validate scrapes and panel PromQL against **production** Prometheus before claiming production validation.

A single Grafana may show Local + Production only if it can securely reach both Prometheus instances; otherwise deploy the same JSON into two Grafana instances.

## Datasource provisioning

| Name | UID | Where |
|------|-----|--------|
| Prometheus-Local | `prometheus-local` | `monitoring/grafana/provisioning/datasources/datasource.yml` (Compose) |
| Prometheus-Production | `prometheus-production` | Example only — `datasource-production.example.yml` |

`allowUiUpdates: false` — Git JSON is source of truth.

## Panel → metric mapping (summary)

| Panel | PromQL core |
|-------|-------------|
| Overall System Health | `avg(up{job=~"aptech4-api\|postgres-exporter\|redis-exporter"})` |
| API Availability | `up{job="aptech4-api"}` |
| API Request Rate | `sum(rate(http_server_requests_seconds_count{application="aptech4-api",uri=~"$uri"}[1m]))` |
| HTTP Success Rate | `sum(rate(...status=~"2.."[5m])) / sum(rate(...[5m]))` |
| API p95 | `histogram_quantile(0.95, sum by (le) (rate(*_bucket[5m])))` |
| Total API Requests | `sum(increase(...[$__range]))` |
| Status pie/bar | `sum by (status) (increase(...[$__range]))` |
| Top endpoints | `topk(10, sum by (uri) (increase(...[$__range])))` |
| Latency p50/p95/p99 | `histogram_quantile` on buckets |
| Error rate 4xx/5xx | `rate` of status class, or `(all_rate * 0)` when traffic exists but no errors |
| Heatmap | `sum by (le) (rate(*_bucket[5m]))` format heatmap |
| Slowest endpoints | `rate(sum)/rate(count)` by uri |
| API process CPU | `process_cpu_usage` |
| JVM heap | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes` |
| Docker VM CPU/mem | cAdvisor `id="/"` |
| GC / threads | `jvm_gc_pause_seconds_sum`, `jvm_threads_live_threads` |
| Postgres health | `pg_up` |
| PG connections | `pg_stat_database_numbackends{datname="aptech4"}` |
| Hikari usage | `hikaricp_connections_active / max` |
| PG utilization | backends `/ on() group_left() pg_settings_max_connections` |
| Redis health/mem | `redis_up`, `redis_memory_used_bytes` (no % if max=0) |
| Redis clients/cmds | `redis_connected_clients`, `rate(redis_commands_processed_total)` |
| Ollama | `up{job="ollama"}` → N/A; optional cAdvisor name=~ollama; chat rates |
| DEMO Availability SLI | `aptech_slo:availability_ratio:30d` |
| DEMO Latency SLI | `aptech_slo:latency_ratio:30d` (needs `le="0.5"` bucket) |
| DEMO Error budget | `aptech_slo:availability_error_budget_remaining_clamped:30d` |
| Availability SLI (candidate) | `up{job="aptech4-api"}` (no target attached) |

Full expressions live in the dashboard JSON.

## Metric inventory

| Metric | Meaning | Source | Key labels | Unit | Local | Prod | Panels |
|--------|---------|--------|------------|------|-------|------|--------|
| `up` | Scrape success | Prometheus | `job` | 0/1 | Yes | When scraped | Health, SLI |
| `http_server_requests_seconds_count` | Request counter | Micrometer | `application,method,uri,status,outcome` | count | Yes | Same app | Traffic, errors |
| `http_server_requests_seconds_sum` | Latency sum | Micrometer | same | seconds | Yes | Same | Slowest |
| `http_server_requests_seconds_bucket` | Latency histogram | Micrometer (`percentiles-histogram`) | +`le` | seconds | Yes | Same | p50/p95/p99, heatmap |
| `process_cpu_usage` | Process CPU ratio | Micrometer | `application` | ratio | Yes | Same | Infra |
| `jvm_memory_used_bytes` / `_max_bytes` | JVM memory | Micrometer | `area,id` | bytes | Yes | Same | Heap |
| `jvm_gc_pause_seconds_*` | GC pause | Micrometer | `action,cause,gc` | seconds | Yes | Same | GC |
| `jvm_threads_live_threads` | Live threads | Micrometer | | gauge | Yes | Same | Threads |
| `hikaricp_connections_*` | Pool | Micrometer | `pool` | conn | Yes | Same | Hikari |
| `pg_up` | PG reachable | postgres-exporter | | 0/1 | Yes | Same exporter | PG health |
| `pg_stat_database_numbackends` | Backends | postgres-exporter | `datname` | conn | Yes | Same | PG connections |
| `pg_settings_max_connections` | Max conn | postgres-exporter | | conn | Yes | Same | PG util |
| `redis_up` | Redis reachable | redis-exporter | | 0/1 | Yes | Same | Redis health |
| `redis_memory_used_bytes` | Used mem | redis-exporter | | bytes | Yes | Same | Redis mem |
| `redis_memory_max_bytes` | maxmemory | redis-exporter | | bytes | Yes (=0 unlimited) | Depends | Util only if >0 |
| `redis_connected_clients` | Clients | redis-exporter | | gauge | Yes | Same | Redis |
| `redis_commands_processed_total` | Commands | redis-exporter | | counter | Yes | Same | Redis |
| `container_*` | cgroup CPU/mem | cAdvisor | often only `id="/"` on Mac | cores/bytes | Partial | Better on Linux | Infra |
| `aptech_chat_*` | Chat | API Micrometer | `endpoint,handler,outcome` | counter | Yes | Same | Optional chat |
| `up{job=ollama}` | Ollama scrape | — | — | — | **No job** | Not configured | N/A |
| `aptech_slo:*:30d` | DEMO SLO recordings | Prometheus rules | — | ratio/count | After rules load + traffic | Same rules | Row 6 |
| `node_*` | Host node | node-exporter | — | — | **Not shipped** | Optional later | N/A |

Verified Local labels for HTTP include `uri` templates such as `/api/v1/products/{id}`, `/api/v1/orders/{orderId}`, and checkout **`status=201`** (`CheckoutController` returns `201 CREATED`).

**Auth failures before the controller** (e.g. unauthenticated `/api/v1/cart`) are often recorded as `uri="UNKNOWN"` with `status="401"`. They are **not** included in `/api/v1/.*` traffic panels by design (normalized route templates only).

## Known platform limitations

- **Docker Desktop macOS + cAdvisor**: scrape may be `up` while only VM root (`id="/"`) exists — no per-container `name`. Panels label this as Docker VM, not API container.
- **Redis maxmemory=0**: do not chart utilization %.
- **Ollama**: no scrape target; chat metrics exist on API when AI enabled.
- **SLO**: readiness only — no targets, no Alertmanager, no error-budget fabrication.
- **N/A vs 0**: missing series → N/A; never use bare `or vector(0)` to hide broken scrapes. Error panels may use `(error_rate) or ((all_api_rate)*0)` so **measured zero** appears when traffic exists without errors.

## Troubleshooting

| Symptom | Check |
|---------|--------|
| API target DOWN | `make deploy-local`; curl loopback `/actuator/prometheus` |
| Empty HTTP panels | Generate `/api/v1` traffic; wait ≥15s scrape; confirm `$uri` / All |
| Empty container name series on Mac | Expected; use VM + process/JVM panels |
| Old dashboards still in UI | Recreate Grafana container (volume keeps DB — file provisioner updates by UID); only `aptech4-sre-overview` remains in Git |
| Datasource missing | Ensure `Prometheus-Local` provisioned; restart Grafana |
| Compose invalid | `docker compose --profile monitoring config` |

## DEMO SLI / SLO (not a production SLA)

These targets are explicitly labeled **DEMO SLO** for teaching/demo. They are **not** a contractual SLA and must not be cited as 30-day production compliance until 30 days of Prometheus history exist under the same eligibility policy.

| | DEMO SLO A — Availability | DEMO SLO B — Latency |
|--|---------------------------|----------------------|
| Target | **99.9%** | **95%** of requests ≤ **500ms** |
| Window | Rolling **30 days** | Rolling **30 days** |
| Recording rules | `monitoring/prometheus/rules/aptech4_slo.yml` | same |

### Eligibility policy (endpoints)

**Include:** `application="aptech4-api"`, `uri=~"/api/v1/.*"`.

**Exclude from all SLO math:**

| Exclusion | Rationale |
|-----------|-----------|
| `/api/v1/health` | Liveness/readiness-style health traffic (dominates counters if included) |
| `/actuator/**` | Already outside `/api/v1/.*` — internal monitoring scrapes |
| `/ws` | WebSocket upgrade, not REST request SLI |
| `uri="UNKNOWN"` | Auth failures before MVC route templates |

### Availability status policy

| Class | Role |
|-------|------|
| **2xx, 3xx** | Good events |
| **5xx** | Bad events |
| **4xx** (400/401/403/404/…) | **Excluded** from good and total — expected client/validation/auth errors |

\[
\mathrm{SLI}_{avail} = \frac{\mathrm{increase}(\mathrm{count}_{2xx|3xx})[30d]}{\mathrm{increase}(\mathrm{count}_{2xx|3xx|5xx})[30d]}
\]

Error budget (allow **0.1%** bad):  
`remaining = 1 - (1 - SLI) / 0.001`  
Display uses clamped series `…_remaining_clamped:30d` (0–100%); raw `…_remaining:30d` kept for debug.

### Latency status / histogram policy

- Total events: eligible URI request **count** (all statuses on eligible URIs).
- Good events: `increase(http_server_requests_seconds_bucket{le="0.5"}[30d])` — **not** p95 and **not** averages.
- Micrometer config: `management.metrics.distribution.slo.http.server.requests` includes **500ms** so Prometheus exposes `le="0.5"`.

\[
\mathrm{SLI}_{lat} = \frac{\mathrm{increase}(\mathrm{bucket}_{le=0.5})[30d]}{\mathrm{increase}(\mathrm{bucket}_{le=+Inf})[30d]}
\]

Denominator uses `le="+Inf"` **only for series that also have `le="0.5"`** (`and ignoring (le)`), so older histogram history from before the 500ms SLO bucket existed cannot inflate the total.

Latency error budget allow **5%** slow: `remaining = 1 - (1 - SLI) / 0.05`.

### Recorded metric names

| Recording rule | Meaning |
|----------------|---------|
| `aptech_slo:availability_ratio:30d` | Availability SLI |
| `aptech_slo:availability_error_budget_remaining:30d` | Raw avail budget remaining |
| `aptech_slo:availability_error_budget_consumed:30d` | Raw avail budget consumed |
| `aptech_slo:availability_error_budget_remaining_clamped:30d` | Display 0..1 |
| `aptech_slo:latency_ratio:30d` | Latency SLI |
| `aptech_slo:latency_error_budget_remaining:30d` | Raw latency budget remaining |
| `aptech_slo:latency_error_budget_consumed:30d` | Raw latency budget consumed |
| `aptech_slo:latency_error_budget_remaining_clamped:30d` | Display 0..1 (+ secondary on Latency panel) |
| `aptech_slo:availability_*_events:30d` / `latency_*_events:30d` | Event counts for debug |

### Insufficient data

If the denominator has **no observations** in the 30d window (or `le="0.5"` is missing), ratio series are **absent**. Grafana Row 6 shows **Insufficient data** — never a fabricated 100% or 0% failure.

Do **not** claim 30-day DEMO SLO compliance until retention ≥ 30d (`PROMETHEUS_RETENTION`, default **30d**) and continuous scrapes have filled the window.

### Row 6 panels

Operational dashboard default time range: **Last 6 hours**. DEMO SLO panels still read **fixed 30d recording rules** (independent of the Grafana picker). Short local Prometheus history is **not** 30-day compliance.

1. **Availability SLI (DEMO 99.9%)** — SLI, target, budget remaining (clamped), budget consumed (raw, may exceed 100%), good/bad/total events  
2. **Latency SLI (DEMO 95% ≤ 500ms)** — SLI, target, budget remaining/consumed, good/slow/total histogram events  
3. **Error Budgets (DEMO)** — avail + latency remaining (clamped) and consumed (raw)  
4. **Slow requests by endpoint** — `increase(le=+Inf) − increase(le=0.5)` per `uri` (30d rules window)  
5. **DEMO SLO window note** — event-volume diagnostics from the same recording rules  

Missing recording-rule series → **Insufficient data** (not 0% / healthy).

## Layout

```
monitoring/
  prometheus/prometheus.yml
  scripts/gen_sre_overview.py
  grafana/provisioning/datasources/datasource.yml
  grafana/provisioning/datasources/datasource-production.example.yml
  grafana/provisioning/dashboards/dashboards.yml
  grafana/dashboards/aptech4-sre-overview.json   # ONLY dashboard JSON
```
