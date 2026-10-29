# Aptech-4 Project Playbook

Canonical operational manual for the Aptech semester 4 e-commerce graduation project.  
**Chat history is not a source of truth.** Persist decisions, status, evidence, and next actions in repository files.

## 1. Purpose and scope

Build a complete e-commerce system for graduation demo:

| Layer | Technology |
|-------|------------|
| Backend | Java 21, Spring Boot 4.x REST, Spring Security, JWT + refresh, RBAC |
| Data | PostgreSQL, Liquibase, Redis cache |
| Web | React + TypeScript — customer storefront + admin dashboard |
| Mobile | Flutter Android customer app + release APK |
| Ops | Docker Compose, root Makefile, Nginx, AWS EC2 (SSH, public IPv4, HTTP) |
| Quality | Backend unit/integration/security tests, frontend Vitest, Flutter tests, seed/reset, docs |

Out of scope unless explicitly approved: microservices, HTTPS/TLS termination for school demo, real payment gateways (mock payment only), multi-tenant SaaS.

## 2. Architecture summary

```
[React Storefront] ─┐
[React Admin]      ─┼──► Nginx (optional) ──► Spring Boot API ──► PostgreSQL
[Flutter Android]  ─┘                              │
                                                   └──► Redis
```

- **Modular monolith** (not microservices).
- React and Flutter share one REST API.
- Local: Compose for Postgres/Redis (+ optional full stack). EC2: Compose/containers behind Nginx :80; DB/Redis private.

Authoritative decisions: `.cursor/decisions/`. Roadmap: `.cursor/plans/`.

## 3. Repository structure (target)

```
aptech-4/
├── .cursor/                 # Playbook, rules, plans, state, ADRs
├── AGENTS.md                # Short agent entrypoint (non-duplicative)
├── Makefile                 # First-class local/EC2/APK workflows
├── docker-compose.yml       # Local + production-like profiles
├── .env.example
├── backend/                 # Spring Boot (Maven Wrapper)
├── frontend/                # React + Vite (storefront + admin)
├── mobile/                  # Flutter Android customer app
├── deploy/                  # Nginx, EC2 scripts, compose prod overlays
├── docs/                    # Technical + demo documentation
└── .github/workflows/       # CI
```

Until directories exist, treat this as the target layout (see phase plans).

## 4. Authoritative state

| File | Role |
|------|------|
| `.cursor/state/TASKS.md` | **Authoritative task registry** (IDs, status, evidence) |
| `.cursor/state/PROGRESS.md` | Phase summary, counts, next task |
| `.cursor/state/CURRENT_SESSION.md` | Live session checkpoint |
| `.cursor/state/BLOCKERS.md` | Unresolved blockers |
| `.cursor/plans/*.md` | Phase objectives, ACs, verification |
| `.cursor/decisions/ADR-*.md` | Architecture decisions |
| `.cursor/rules/*.mdc` | Concise always-on / scoped agent rules |

Statuses (only): `TODO` | `IN_PROGRESS` | `BLOCKED` | `IN_REVIEW` | `DONE` | `DEFERRED`

## 5. Task IDs and dependencies

- Format: `P{phase}-T{nn}` (e..g. `P01-T02`). Never renumber existing IDs.
- Dependencies listed per task; do not mark dependents `DONE` while blockers remain.
- Scope change: mark `DEFERRED` with reason, or supersede with a new ID referencing the old one.
- Do not delete completed tasks.

**DONE requires:** ACs satisfied + code/docs exist + verification commands executed + results inspected + failures fixed or scope-excluded + evidence recorded.

## 6. Session-start checklist

Copy and follow every new Agent session:

1. [ ] Read this playbook
2. [ ] Read `PROGRESS.md`, `TASKS.md`, `CURRENT_SESSION.md`, `BLOCKERS.md`
3. [ ] Read the relevant `.cursor/plans/0N-*.md`
4. [ ] `git status` + inspect relevant files (reconcile tracking vs reality)
5. [ ] Confirm next task ID is still correct
6. [ ] Pick the smallest useful verifiable task
7. [ ] Continue — do **not** restart Phase 0 after context reset
8. [ ] Do **not** ask the user to restate information already in the repo

## 7. Selecting the next task

1. Prefer `IN_PROGRESS` or `IN_REVIEW` with clear remaining work.
2. Else first `TODO` whose dependencies are all `DONE` and not blocked by `BLOCKERS.md`.
3. Prefer vertical-slice / prerequisite infrastructure over optional polish.
4. Skip tasks needing missing tools only if a later independent task can proceed; otherwise install/document blocker.

## 8. Updating status

- Set `IN_PROGRESS` when starting; record files touched.
- After verification: `DONE` with commands + outcomes, or `IN_REVIEW`/`BLOCKED` if env limits verification.
- Update `PROGRESS.md` counts from `TASKS.md` (do not invent percentages).

## 9. Completion quality bar

Creating a file or generating code without verification is **not** completion.  
Compile-only success ≠ integration/security/deploy correctness.  
Passing a suite ≠ feature complete if required behavior is missing.

## 10. Verification strategy

Smallest meaningful command first, then broaden at phase gates:

| Area | First check | Gate |
|------|-------------|------|
| Backend | Targeted JUnit | Full `./mvnw test` |
| Liquibase | Migrate fresh Postgres | Compose health |
| React | Targeted Vitest | lint + `tsc` + build |
| Flutter | Targeted test + analyzer | Release APK |
| Makefile | Dry-run / help | Safe target execution |
| Compose | `docker compose config` | Healthchecks |
| EC2 | Script validation locally | Remote only with approved host |

Record exact commands and observed outcomes. Never remove tests or weaken security to force green.

## 11. Blocked tasks

1. Reproduce and document in `BLOCKERS.md`.
2. Set task `BLOCKED`; leave dependents `TODO`.
3. Propose resolution (install tool, user approval, design ADR).
4. Unblock → re-verify → then `DONE`.

## 12. Architecture decisions

Important choices → `.cursor/decisions/ADR-NNN-slug.md` from `ADR-000-template.md`.  
Examples: modular monolith, JWT strategy, inventory transactions, Flutter API URL, Redis invalidation, Compose profiles, local vs EC2, seed vs test data.  
Skip ADRs for trivial implementation details.

## 13. Session-end checklist

1. [ ] Active task status + evidence updated in `TASKS.md`
2. [ ] `CURRENT_SESSION.md` has objective, task ID, changes, commands/outcomes, remaining work, **exact next action**
3. [ ] `PROGRESS.md` counts and next task refreshed
4. [ ] New blockers in `BLOCKERS.md`
5. [ ] New ADRs written if decisions were made
6. [ ] No orphan `IN_PROGRESS` without checkpoint
7. [ ] User can resume via the prompt below with zero chat history

## 14. Avoiding duplicate work

- Search `TASKS.md` for `DONE` before reimplementing.
- Diff against Git before rewriting files.
- Prefer extending existing modules over parallel scaffolds.

## 15. Makefile workflows (when present)

Typical targets (exact names tracked in tasks / Makefile help):

- **Local dev:** start/stop Postgres+Redis; run API/frontend in dev mode
- **Test/build:** backend, frontend, flutter tests/builds
- **Demo data:** seed / safe reset (destructive = explicit)
- **Local prod-like:** compose up full stack, health, logs
- **EC2:** deploy/status/logs over SSH (needs host config; not localhost)
- **APK:** release build with explicit `API_BASE_URL`
- **Cleanup:** non-destructive by default; volume wipe explicit

Local deploy must not need AWS credentials. EC2 must not silently target localhost.

## 16. Environment modes

| Mode | Meaning |
|------|---------|
| Local development | Hot reload; Compose for deps only |
| Local demo / prod-like | Full Compose stack as graduation rehearsal |
| EC2 deployment | Remote SSH + Nginx HTTP on public IPv4; school demo only |

## 17. Copy-paste resume prompt

```
Resume Aptech-4 from the repository playbook (do not rely on chat history).

Project root: /Users/Martin/Documents/APTECT_PROJECT_4/aptech-4

1. Read .cursor/PROJECT_PLAYBOOK.md
2. Read .cursor/state/PROGRESS.md, TASKS.md, CURRENT_SESSION.md, BLOCKERS.md
3. Read the relevant phase plan under .cursor/plans/
4. Inspect git status and reconcile tracking vs code
5. Continue the exact next task recorded in PROGRESS.md / CURRENT_SESSION.md
6. Verify with the task's commands, update state files, checkpoint before stopping

Follow .cursor/rules/ at all times.
```

## 18. Tooling notes (host assessment)

Recorded at planning time; re-verify each session:

- Present: Node.js/npm, Docker + Compose, GNU Make, Homebrew, Git
- Missing at P00: JDK 21, Maven (use wrapper after scaffold), Flutter SDK, GitHub CLI

Missing tools → `BLOCKED`/`IN_REVIEW` with evidence; prefer Docker-based backend builds when JDK absent.
