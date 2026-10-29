# Scripts

| Script | Purpose |
|--------|---------|
| `check-dotenv.sh` | Verify required `.env` keys (prints set/missing only — never values) |
| `load-dotenv.sh` | `source` helper for shell workflows |
| `dev-flutter.sh` | Host FVM/Flutter helpers |
| `mobile-docker.sh` | Flutter + Shorebird via Docker (loads `.env`, never logs tokens) |

Prefer `make check-env` / `make setup` / `make mobile-*` over calling scripts directly.

### Mobile Make targets (Docker)

| Target | Needs token? | Notes |
|--------|--------------|--------|
| `mobile-build-image` | No | Exit 0 only if image inspect succeeds after compose build |
| `mobile-versions` / `mobile-doctor` | No* | Doctor fails if `shorebird doctor` fails |
| `mobile-shorebird-auth` | **Yes** | `shorebird account` + doctor |
| `mobile-analyze` / `test` | No | |
| `mobile-build-android` / `mobile-build-apk` | No | Debug APK; pass `API_BASE_URL=` → `--dart-define` |
| `mobile-build-release-apk` | No | Release APK (debug signing only); same dart-define |
| `mobile-build-release-aab` | No | Release AAB (Play-style bundle; debug signing only) |
| `mobile-shorebird-workflow` | No* | Read-only CLI/help + `shorebird.yaml` check; **no publish** |
| `mobile-shorebird-init` | **Yes** | Skips if `shorebird.yaml` exists |
| `mobile-shorebird-release\|patch` | **Yes** + `CONFIRM=yes` | **Publishes** — not for routine CI |

\*Token optional for doctor URL checks; required to prove account auth.
