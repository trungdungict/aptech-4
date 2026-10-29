# BLOCKERS

Unresolved problems that prevent task completion. Clear entries when resolved and update `TASKS.md`.

---

## B-001 — Host JDK / nested Testcontainers

- **Date:** 2026-10-28
- **Affects:** Historical Docker-only verification; nested TC from Maven-in-Docker
- **Reproduction (historical):** `java -version` missing; nested TC via Docker Desktop sock → empty HTTP 400 Info on Engine 29 with TC 1.21.3
- **Root cause:** Toolchain gap + Docker Engine 29 min API 1.44 vs older docker-java
- **Mitigation (2026-10-28):**
  1. Host **OpenJDK 21** installed (`brew install openjdk@21` → `/opt/homebrew/opt/openjdk@21`)
  2. Testcontainers **1.21.4** + `docker-java.properties` `api.version=1.44` — host TC bootstrap IT green (P07-T01)
  3. Compose `IT_*` remains verified path for full suite / Docker Maven; nested DinD TC still flaky
- **Status:** MITIGATED (host JDK + TC OK; nested DinD optional)

---

## B-002 — Flutter SDK not installed on host

- **Date:** 2026-10-28
- **Affects:** Optional host-FVM workflow only
- **Reproduction:** `command -v flutter` / `dart` → not found on host
- **Mitigation:** Docker toolchain verified 2026-10-28 (`make mobile-*`, image `aptech4-mobile-toolchain:3.47.8`, P11-T01 DONE)
- **Status:** MITIGATED (Docker path authoritative; host install still optional)

---

## B-005 — Docker daemon socket not usable

- **Date:** 2026-10-28
- **Affects:** Agent sandbox without full permissions; Desktop must be running
- **Reproduction (historical):** `permission denied` on `~/.docker/run/docker.sock` from restricted sandbox
- **Mitigation:** Docker Desktop running + `required_permissions: all` / normal terminal → `make mobile-docker-check` OK; image rebuild verified
- **Status:** MITIGATED (re-open if Desktop stopped)

---

## B-003 — EC2 host not configured

- **Date:** 2026-10-28
- **Affects:** P15-T03 remote smoke
- **Reproduction:** No `EC2_HOST` / SSH key documented in repo (correct — not invented)
- **Root cause:** Awaiting user-provided host and approval
- **Proposed resolution:** User supplies host, key path, admin IP; approve real deploy; until then implement scripts only
- **Status:** OPEN (expected)

---

## B-004 — GitHub CLI (`gh`) missing

- **Date:** 2026-10-28
- **Affects:** Optional PR automation; CI still doable via git push + Actions UI
- **Reproduction:** `gh` not found
- **Proposed resolution:** `brew install gh` if needed; not blocking core delivery
- **Status:** OPEN (low priority)

---

## D-MOBILE-DEVICE — Physical device E2E (deferred by product decision)

- **Date:** 2026-10-29
- **Affects:** End-to-end mobile ↔ live API validation
- **Decision:** Local mobile work stops at Docker automated checks, APK/AAB artifacts, and Shorebird CLI readiness. No Android Emulator on MacBook; no `adb install`; no live phone testing yet.
- **Resume when:** Backend is deployed on EC2 (or otherwise reachable from a real device) with a stable `API_BASE_URL` (prefer HTTPS). Then rebuild APK/AAB with that define and test on a physical phone.
- **Not a blocker for:** P12 build/docs, Shorebird auth/init/workflow documentation, or starting P13 Redis work.
- **Status:** DEFERRED (intentional)
