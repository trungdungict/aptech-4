# PROGRESS

Last updated: 2026-10-29 (Customer chat SSE spaces + deterministic intents — ad-hoc; roadmap still 54/64)

## Summary counts (from TASKS.md)

| Status | Count |
|--------|------:|
| DONE | 54 |
| IN_PROGRESS | 0 |
| IN_REVIEW | 0 |
| BLOCKED | 0 |
| TODO | 10 |
| DEFERRED | 0 |
| **Total** | **64** |

Completion ratio (DONE/total): **54/64**

## Current phase

**P14** local prod-like deployment **complete**. Next: **P15** EC2 (or P16 smoke pack).

## Verified completed (recent)

- **P14-T01 / P14-T02 / P14-T03** — Compose stack + seed/reset
- **Order notifications (REST)** — ADR-017; persistent rows + bell UI
- **Notification WebSocket (ad-hoc, 2026-10-29)** — ADR-018 STOMP `/ws`, JWT CONNECT, after-commit push, nginx Upgrade
- **WS latency (ad-hoc, 2026-10-29)** — broker heartbeats + dedicated STOMP channel executors + app scheduler isolation
- **Local AI assistant (ad-hoc, 2026-10-29)** — ADR-019 Spring AI 2.0.0 + Ollama optional profile; Customer/Admin tools; EN default + VI
- **Live Ollama verify (ad-hoc, 2026-10-29)** — `llama3.2` pulled; chat `READY`; Customer tools match seed orders/catalog; Admin counts/sales match when tools invoked; SSE deltas+done (API+nginx) after SecurityContext fix; Ollama-down → 503 in `APTECH_AI_TIMEOUT_SECONDS`; AI-off app OK; FE **90**; BE **134** (catalog cache flake intermittent)
- **Admin chat analytics authenticity (ad-hoc, 2026-10-29)** — Fixed auth-disclaimer + invented “12” on “How many orders need processing?” via deterministic `attentionCounts` (`PAID`+`PROCESSING`); live DB+API+SSE agree on **53**; FE ChatPanel scroll/`min-h-0`; BE **149** / JaCoCo LINE **0.72**; FE **91**
- **SSE incomplete chunked encoding + chat UI (ad-hoc, 2026-10-29)** — Root cause: async SSE completion Access Denied without request-scoped SecurityContext; fixed via `RequestAttributeSecurityContextRepository` + clean terminal SSE; friendly Admin prose; FE SSE parser + layout; verified on **8080/8088** + browser; FE **98**; roadmap still **54/64**
- **Admin chat intents (ad-hoc, 2026-10-29)** — Deterministic router for sales / paid list / price filter; sales **56 / 219737000 VND**; price `>` **40**; paid list **23**; `$` clarifies; BE **162**; FE **100**; **54/64**
- **Customer chat render + intents (ad-hoc, 2026-10-29)** — JSON-encoded SSE deltas preserve spaces; bubble wrap/`min-w-0`; deterministic latest-order / products-under / checkout; FE **106**; chat package tests green; live API+nginx SSE join==done; **54/64**
- **Customer order-count auth (ad-hoc, 2026-10-29)** — Deterministic `ORDER_COUNT` via JWT `listMine`; auth/tool failures never become 0; SSE 401 → Sign in required; live count **56** matches `/orders`; FE **109**; **54/64**
- **Customer paid/oos/admin-msg (ad-hoc, 2026-10-29)** — Status list/count intents; seed OOS available=0; Admin-only store analytics copy; checkout without `simulateFailure`; live paid **22**; **54/64**
- **Local monitoring stack (ad-hoc, 2026-10-29)** — Optional Compose profile `monitoring` (Prometheus/Grafana/exporters/cAdvisor); Actuator Prometheus + chat metrics; provisioned dashboards; `docs/MONITORING.md`; **54/64**

## Current blockers

B-001/B-002/B-005 mitigated; B-003 EC2; B-004 `gh`; D-MOBILE-DEVICE deferred.

## Next recommended task

**`P15-T01` — Nginx + remote compose overlays**, or **`P16-T02`** local smoke script if EC2 is gated.

## Recent non-roadmap tooling / features

- Postman + order notifications REST + STOMP realtime — do **not** change 54/64 until matching TASKS are closed
