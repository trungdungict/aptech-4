# P02 + P14 — Docker Compose and Makefile (local)

## Objective

First-class local dependency stack and Makefile workflows; later production-like local deployment.

## Prerequisites

- P01 structure and `.env.example`
- Docker + Compose available (verified at P00)

## P02 — Foundation

- `docker-compose.yml` with Postgres + Redis (+ healthchecks)
- Root `Makefile`: `up-deps`, `down-deps`, `ps`, `logs`, `help`
- Document profiles if used

## P14 — Prod-like local

- API + web (+ optional nginx) services
- `make deploy-local`, `health-local`, `logs-local`
- Seed/reset targets wired to backend
- Normal down preserves volumes; `reset`/`down-volumes` explicit

## Acceptance criteria

- `docker compose config` valid
- Deps healthy; Makefile targets executed and evidenced
- Local deploy does **not** require AWS credentials

## Verification

```bash
docker compose config
make help
make up-deps && make health-deps   # names may vary — match Makefile
```

## Risks

- Port conflicts on host
- Apple Silicon image availability — prefer multi-arch official images

## Phase complete when

P02 then P14 task groups verified.
