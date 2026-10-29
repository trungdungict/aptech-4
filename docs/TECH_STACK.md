# Technology stack and version pins

Intent for Aptech-4. Exact patch versions are locked in lockfiles/`pom.xml` when each app is scaffolded.

| Area | Choice | Pin intent |
|------|--------|------------|
| Language (API) | Java | **21** (LTS) |
| Framework | Spring Boot | **4.1.2** (ADR-003; Initializr no longer serves 3.x) |
| Build | Maven Wrapper | Commit `mvnw`; no global Maven required |
| Security | Spring Security + JWT | Access + refresh; RBAC `CUSTOMER`/`ADMIN` |
| DB | PostgreSQL | **16.x** — Compose image `postgres:16-alpine` |
| Migrations | Liquibase | Boot-managed |
| Cache | Redis | **7.x** — Compose image `redis:7-alpine` |
| API docs | springdoc-openapi | Compatible with Boot 3 |
| Tests (API) | JUnit 5, Mockito, Testcontainers **1.21.4+** | Engine 29 needs API ≥1.44 (ADR-009) |
| Web | React + TypeScript (strict) | Vite 6.x / React 18 or 19 as scaffold chooses |
| UI | Tailwind CSS + shadcn/ui | — |
| Data fetching | TanStack Query | v5 |
| Routing / forms | React Router, RHF + Zod | — |
| Web tests | Vitest + Testing Library | — |
| Mobile | Flutter / Dart stable | Pin channel at P11 scaffold |
| Mobile state/nav/http | Riverpod, GoRouter, Dio | — |
| Containers | Docker Compose | v2+ CLI (`docker compose`) |
| Edge (demo) | Nginx | Alpine stable |
| Local LLM (optional) | Ollama + Spring AI **2.0.0** | Profile `ai`; default model `llama3.2` (ADR-019) |
| Host automation | GNU Make | Root `Makefile` |
| CI | GitHub Actions | — |

## Host assessment (2026-10-28)

- Available: Node **v26.5.1**, npm **12.0.2**, Docker **29.6.2**, Compose **v5.3.1**, Make **3.81**
- Missing: JDK 21, Flutter SDK, GitHub `gh`

Re-verify with version commands at each session start if builds fail.
