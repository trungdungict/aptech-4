# P00–P17 Master plan

## Goal

Deliver a verifiable Aptech semester 4 e-commerce system with persistent Cursor playbook so any new Agent session resumes without chat history.

## Phase map

| Phase | Name | Plan file | Depends on |
|-------|------|-----------|------------|
| P00 | Assessment + planning infrastructure | this file + all plans/state | — |
| P01 | Foundation, structure, versions | `01-foundation.md` | P00 |
| P02 | Compose Postgres/Redis + Makefile foundation | `06-docker-makefile-local.md` (early) | P01 |
| P03 | Liquibase + domain + backend architecture | `02-backend-database-auth.md` | P02 |
| P04 | Auth JWT refresh RBAC | `02-backend-database-auth.md` | P03 |
| P05 | Catalog categories search admin APIs | `02-backend-database-auth.md` | P04 |
| P06 | Cart inventory checkout mock pay orders | `02-backend-database-auth.md` | P05 |
| P07 | Backend tests + fixtures | `03-backend-tests-mock-data.md` | P04–P06 (incremental OK) |
| P08 | Demo seed + safe reset | `03-backend-tests-mock-data.md` | P03, P06 |
| P09 | React customer storefront | `04-frontend-customer-admin.md` | P05–P06 API contracts |
| P10 | React admin dashboard | `04-frontend-customer-admin.md` | P05–P06 admin APIs |
| P11 | Flutter customer app | `05-flutter-android.md` | P05–P06 APIs |
| P12 | Flutter API config + release APK | `05-flutter-android.md` | P11 |
| P13 | Redis caching + invalidation | `02` / backend tasks | P05, P02 |
| P14 | Local prod-like deployment | `06-docker-makefile-local.md` | P08, API + web |
| P15 | EC2 SSH Nginx HTTP | `07-ec2-deployment.md` | P14 |
| P16 | CI + E2E smoke + docs | `08-integration-final-qa.md` | P14 |
| P17 | Final acceptance + demo prep | `08-integration-final-qa.md` | P16 |

## Execution policy

1. Complete P00 planning files and task registry first.
2. Implement in dependency order; early **vertical slice** (auth + one product list + cart stub) before full breadth.
3. Do not start a phase whose prerequisites are unverified.
4. Checkpoint state after every meaningful task.
5. EC2 real deploy only with explicit host + approval.

## Definition of project completion

All acceptance tasks in P17 `DONE` with evidence; Makefile workflows verified; demo seed deterministic; docs and resume prompt usable by a cold Agent session.

## Risks (global)

- JDK 21 / Flutter missing on host → Dockerized builds or `BLOCKED` until installed.
- EC2 credentials/host unknown → scripts ready, remote tasks stay `TODO`/`BLOCKED`.
- Context limits → prefer small tasks; never leave `IN_PROGRESS` without checkpoint.
