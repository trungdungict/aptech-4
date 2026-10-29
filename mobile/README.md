# Mobile (Flutter) — Aptech-4 customer app

Android customer client. **Preferred toolchain: Docker** (pinned Flutter + Android SDK + Shorebird). Host Flutter/FVM is optional and not required for the workflow below.

Shorebird uses **Shorebird Cloud** for OTA (no self-hosted OTA server).

---

## Current verification scope (product decision)

| In scope now | Deferred (after backend on EC2 + real phone) |
|--------------|-----------------------------------------------|
| Docker toolchain + automated checks | Android Emulator on MacBook |
| Debug/release **APK** + release **AAB** artifacts | `adb install` / device UI smoke |
| Shorebird **auth**, **init**, CLI **workflow** docs | Live end-to-end against EC2 API |
| Build-time `API_BASE_URL` documentation | Claiming live API connectivity |

**Do not** treat “APK built” as “app works on a phone against production.” Device testing is scheduled after the API is reachable from a physical device.

---

## Verified status (2026-10-29)

| Item | Result |
|------|--------|
| Image | `aptech4-mobile-toolchain:3.47.8` |
| Flutter / Dart / Shorebird | **3.47.8** / **3.13.7** / CLI in image |
| `make mobile-analyze` / `mobile-test` | Run in Docker (see session evidence) |
| Debug APK / release APK / release AAB | Built + size > 0 (paths below) |
| `shorebird.yaml` | Present (`app_id` public, not a secret) |
| Shorebird auth | `make mobile-shorebird-auth` (token never printed) |
| Shorebird **publish** release/patch | **Not performed** in this workflow (needs `CONFIRM=yes`) |
| Emulator / device / live API | **Not run** |

---

## 1. Prerequisites

- Docker Desktop running (`make mobile-docker-check`)
- Repo root `.env` from `.env.example` (never commit real secrets)
- For Shorebird Cloud auth/init/publish: `SHOREBIRD_TOKEN` in `.env` only
- **No** host Flutter/Dart required for `make mobile-*`

## 2. Toolchain setup

```bash
# From repo root (aptech-4/)
make mobile-docker-check
make mobile-build-image          # first time / after Dockerfile pin change
make mobile-versions
make mobile-doctor               # flutter doctor + shorebird doctor
```

## 3. Environment check

```bash
make setup                       # creates .env only if missing
make check-env                   # keys present/missing — values never printed
```

Relevant keys (see root `.env.example`):

| Key | Purpose |
|-----|---------|
| `API_BASE_URL` | Forwarded into `--dart-define` at **build** time |
| `SHOREBIRD_TOKEN` | Shorebird Cloud API key (gitignored `.env` only) |
| `FLUTTER_VERSION` | Image tag pin (default `3.47.8`) |

## 4. `API_BASE_URL` (build-time only)

The app reads **one** compile-time define (ADR-002 / ADR-014):

```text
--dart-define=API_BASE_URL=<url-including-/api/v1>
```

| Layer | Role |
|-------|------|
| `make … API_BASE_URL=…` | Overrides `.env` when non-empty |
| `.env` `API_BASE_URL` | Default for Docker/Make when Make var empty |
| Running app | Uses **only** the dart-define — **not** OS env at runtime |

Default when unset: `http://10.0.2.2:8080/api/v1` (emulator → host). **Never** hardcode a production host in source.

### Reachability

The URL must be reachable from the **app runtime** (future phone/emulator), not from the toolchain container alone.

| Runtime | Typical value | Notes |
|---------|---------------|-------|
| Emulator → host API | `http://10.0.2.2:8080/api/v1` | Emulator alias for host loopback |
| Physical phone → host LAN | `http://<YOUR_LAN_IP>:8080/api/v1` | Do not commit a personal IP |
| Compose API published on host | `http://10.0.2.2:<published_port>/api/v1` | Use host-mapped port, not internal service DNS |
| Compose service name (`backend`) | Only if app ran **inside** Compose network | **Invalid** for normal APK on phone/emulator |
| Production | `https://<public-host>/api/v1` | HTTPS; release blocks cleartext |

`localhost` / `127.0.0.1` on a device means **the device**, not your Mac.

### Cleartext HTTP

- **debug** / **profile**: `usesCleartextTraffic=true` (local HTTP demos)
- **release**: cleartext blocked by default → use HTTPS for production-shaped builds

## 5. Everyday automated checks

```bash
make mobile-pub-get
make mobile-analyze
make mobile-test
```

All run inside Docker. No host Flutter SDK.

## 6. Build artifacts

```bash
# Debug APK (dev installs / sideload later)
make mobile-build-apk API_BASE_URL=http://10.0.2.2:8080/api/v1
# alias:
make mobile-build-android API_BASE_URL=http://10.0.2.2:8080/api/v1

# Release APK (still debug keystore — no production keystore invented)
make mobile-build-release-apk API_BASE_URL=https://api.example.com/api/v1

# Release AAB (Play-style bundle; same debug signing caveat)
make mobile-build-release-aab API_BASE_URL=https://api.example.com/api/v1
```

Scripts **do not delete** an existing artifact before a successful rebuild.

### Output paths (gitignored: `mobile/build/`, `*.apk`, `*.aab`)

| Target | Artifact |
|--------|----------|
| `mobile-build-apk` / `mobile-build-android` | `mobile/build/app/outputs/flutter-apk/app-debug.apk` |
| `mobile-build-release-apk` | `mobile/build/app/outputs/flutter-apk/app-release.apk` |
| `mobile-build-release-aab` | `mobile/build/app/outputs/bundle/release/app-release.aab` |

### How to verify after build

```bash
# Existence + non-zero size (Make targets already print this)
ls -la mobile/build/app/outputs/flutter-apk/
ls -la mobile/build/app/outputs/bundle/release/

# Optional: confirm dart-define string landed in debug kernel blob
python3 - <<'PY'
import zipfile
from pathlib import Path
p = Path('mobile/build/app/outputs/flutter-apk/app-debug.apk')
with zipfile.ZipFile(p) as z:
    blob = z.read('assets/flutter_assets/kernel_blob.bin')
print('has_10.0.2.2', b'10.0.2.2' in blob)
print('has_API_BASE_URL', b'API_BASE_URL' in blob)
PY
```

**Signing note:** `android/app/build.gradle.kts` uses the **debug** `signingConfig` for release builds so local release APK/AAB work without inventing a production keystore. Replace with a real upload keystore before store submission (out of scope here).

## 7. Shorebird: init, release, patch

### Concepts

| Concept | Meaning |
|---------|---------|
| **Release** | Full Android build uploaded to Shorebird Cloud; baseline for OTA. Install this binary (or store build derived from it) on devices. |
| **Patch (OTA)** | Dart/Flutter code-push update applied on top of an **existing** release. Small, fast; does **not** replace the native binary. |

**Patchable (typical):** Dart/Flutter UI and logic under `lib/`, most Flutter-only dependency bumps that do not change native code.

**Needs a new release (not patch-only):** AndroidManifest / Gradle / native plugins, new permissions, asset/engine changes Shorebird cannot patch, Flutter/engine upgrades that require a new baseline, signing/package identity changes.

Always confirm against [Shorebird docs](https://docs.shorebird.dev) for the CLI version in the Docker image.

### Commands (this repo)

```bash
# Auth probe — never prints the token value
make mobile-shorebird-auth

# One-time init (skipped if shorebird.yaml already exists)
make mobile-shorebird-init

# Read-only: yaml present + release/patch --help (NO upload)
make mobile-shorebird-workflow

# PUBLISH — gated; only with explicit confirmation
make mobile-shorebird-release CONFIRM=yes API_BASE_URL=https://api.example.com/api/v1
make mobile-shorebird-patch   CONFIRM=yes API_BASE_URL=https://api.example.com/api/v1
```

Without `CONFIRM=yes`, release/patch **refuse** to run.

### Secret handling

- Put `SHOREBIRD_TOKEN` only in gitignored `.env`
- Never commit `.env`, keystores, or API keys
- Never enable `set -x` around Shorebird commands in shared logs
- `shorebird.yaml` `app_id` is **public** configuration (safe to commit)

### Publish readiness checklist (before you type `CONFIRM=yes`)

1. `make mobile-shorebird-auth` succeeds
2. `mobile/shorebird.yaml` exists
3. You intend to **upload** to Shorebird Cloud (costs / quotas apply per your plan)
4. `API_BASE_URL` matches the environment that installed release will talk to
5. You understand patch vs release (native changes ⇒ new release)

**This documentation workflow does not publish.** Absence of a Cloud release means OTA patch cannot be proven end-to-end until someone runs release with `CONFIRM=yes`.

## 8. Common failures

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `Docker daemon is not reachable` | Desktop stopped / socket perms | Start Docker Desktop; `make mobile-docker-check` |
| Image missing | First run | `make mobile-build-image` |
| `SHOREBIRD_TOKEN is missing` | Empty `.env` | Set token locally; never commit |
| Release/patch exits immediately | No `CONFIRM=yes` | Intentional gate |
| App cannot reach API later | Wrong host (`localhost` on device) | Use `10.0.2.2` / LAN IP / HTTPS public URL |
| Cleartext blocked on release APK | HTTP URL + release manifest | Use HTTPS or debug APK for local HTTP |
| Analyze/test fail in Docker | Code or deps | Fix locally; re-run `make mobile-analyze` / `mobile-test` |

## 9. App feature notes (brief)

- **Auth:** access token memory-only; refresh in `FlutterSecureStorage`; single-flight refresh
- **Catalog/cart/checkout:** Spring contracts under `/api/v1`; cart is server-owned; checkout uses `idempotencyKey` + `addressId` (no fake local payment success)

## 10. Layout

```
mobile/lib/
  app/                 # theme, GoRouter
  core/config|errors|network|storage|util|widgets/
  features/auth|catalog|cart|checkout/
  main.dart
mobile/shorebird.yaml  # public app_id
mobile/docker/         # toolchain Dockerfile
```

## 11. What is explicitly deferred

- Installing/launching Android Emulator on the MacBook
- `adb install` and manual UI testing
- Deploying backend to EC2 / AWS changes
- Publishing Shorebird release or OTA patch without a separate confirmation
- Claiming live API or device integration success

**Next device milestone:** after backend is on EC2 with a public HTTPS (or reachable) URL, rebuild with that `API_BASE_URL` and test on a **physical phone**.
