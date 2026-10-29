# CURRENT_SESSION

## Session objective

Investigate intermittent ~10s WebSocket notification latency (ADR-018 path). Fix only proven causes.

## Task ID

Ad-hoc latency investigation (extends ADR-018). Progress remains **54/64**.

## Changes made

- Enabled STOMP simple-broker heartbeats (`10s/10s`) via dedicated `stompBrokerHeartbeatScheduler`
- Dedicated STOMP inbound/outbound channel executors (override Boot shared `applicationTaskExecutor`)
- Separate `applicationTaskScheduler` for `@Scheduled` (inventory no longer on MessageBroker threads)
- DEBUG-only push timing in `NotificationPushService`
- IT: CONNECTED negotiates non-zero heart-beat; push latency asserts `<2s`
- ADR-018 heartbeat / executor notes

## Commands + outcomes

| Command | Result |
|---------|--------|
| Live probe before (healthy WS) | direct/nginx push after HTTP **p50≈0.2–1.1ms**, max **2.6ms** (n=15); CONNECTED **`heart-beat:0,0`** |
| `NotificationWebSocketIntegrationTest` | **5/0/0** |
| `./mvnw verify` | **121/0/0** |
| FE typecheck / lint / test / build | OK — **85** tests |
| `make build-stack && deploy-local && health-local && smoke-local && postman-validate` | OK |
| Live probe after | CONNECTED **`heart-beat:10000,10000`**; nginx push **p50=0.9ms, p95=1.7ms, max=1.8ms** (n=12) |

## Exact next action

Playbook next: **P15-T01** or **P16-T02** as needed.
