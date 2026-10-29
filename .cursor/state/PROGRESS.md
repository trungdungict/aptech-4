# PROGRESS

Last updated: 2026-10-29 (WS notification latency fix — heartbeats/executors; roadmap still 54/64)

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
- **WS latency (ad-hoc, 2026-10-29)** — broker heartbeats + dedicated STOMP channel executors + app scheduler isolation; verify **121/0/0**; CONNECTED `heart-beat:10000,10000`

## Current blockers

B-001/B-002/B-005 mitigated; B-003 EC2; B-004 `gh`; D-MOBILE-DEVICE deferred.

## Next recommended task

**`P15-T01` — Nginx + remote compose overlays**, or **`P16-T02`** local smoke script if EC2 is gated.

## Recent non-roadmap tooling / features

- Postman + order notifications REST + STOMP realtime — do **not** change 54/64 until matching TASKS are closed
