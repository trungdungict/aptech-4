# ADR-018: STOMP WebSocket push for order notifications

- **Status:** Accepted
- **Date:** 2026-10-29
- **Related:** ADR-017 (persistent notifications), ADR-005 (JWT)

## Context

ADR-017 persisted order notifications with REST + 30s polling. Operators need near-realtime delivery through the same-origin Compose stack (`http://localhost:8088`) without Kafka or a separate push service.

## Decision

1. **Transport:** Spring WebSocket + STOMP simple broker at endpoint `/ws` (no SockJS required for modern browsers).
2. **Auth:** HTTP handshake is permit-all; **STOMP CONNECT** requires `Authorization: Bearer <access JWT>` validated by the existing `JwtService`. Principal name = user id (`AccessTokenPrincipal.getName()`). Never put JWT in the URL.
3. **Destination:** Clients subscribe only to `/user/queue/notifications`. Server pushes with `convertAndSendToUser(userId, "/queue/notifications", NotificationResponse)`.
4. **Timing:** Rows are still written inside the order transaction (ADR-017). STOMP publish runs in `TransactionSynchronization.afterCommit` so rolled-back work never pushes.
5. **Source of truth:** PostgreSQL + REST remain authoritative. Clients refetch via REST on reconnect to recover missed events.
6. **Nginx:** Proxy `/ws` with `Upgrade` / `Connection` headers and long read timeouts; `/api/` unchanged.
7. **Frontend:** `@stomp/stompjs` connects only while authenticated; 30s polling removed; optional **120s** REST fallback only while disconnected.
8. **Heartbeats:** Simple broker must set a `TaskScheduler` + non-zero `heart-beat` (10s/10s). Without this Spring advertises `0,0`, half-open sessions linger, and the first push can block until transport `sendTimeLimit` (default 10s) — seen as intermittent notification latency. STOMP inbound/outbound channels use dedicated executors (not the shared `applicationTaskExecutor`). `@Scheduled` jobs use a separate application scheduler.

## Alternatives considered

1. Keep polling only — rejected (UX lag).
2. SSE — workable but STOMP user-destinations fit multi-recipient fan-out cleanly.
3. SockJS — unnecessary for current browsers + nginx WS proxy.

## Consequences

- Positive: realtime Customer/Admin bells; same security model as REST; Compose same-origin.
- Negative: in-memory simple broker (single instance); reconnect/refetch required after disconnect.
- Follow-ups: sticky sessions / external broker if multi-instance API appears; browser-background delivery remains subject to tab throttling (measure separately from broker heartbeats).
