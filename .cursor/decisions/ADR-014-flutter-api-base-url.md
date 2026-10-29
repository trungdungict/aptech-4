# ADR-014: Flutter API base URL and cleartext policy

- **Status:** Accepted
- **Date:** 2026-10-29
- **Related tasks:** P12-T01, P12-T02
- **Related:** ADR-002

## Context

The Android customer app must reach Spring Boot at different hosts (emulator loopback alias, LAN device, Compose service name, production HTTPS) without hardcoding a developer’s LAN IP or shipping insecure HTTP defaults for production.

## Decision

1. **Build-time only:** API endpoint is compiled via `--dart-define=API_BASE_URL=…` (read by `ApiConfig.fromEnvironment`). A process environment variable named `API_BASE_URL` is only a Makefile/script convenience to *pass into* that dart-define — it is not read at app runtime.
2. **Safe default:** When unset, default is `http://10.0.2.2:8080/api/v1` (Android emulator → host machine). Never default to a production hostname.
3. **Single client:** All Dio clients use `apiConfigProvider` / `ApiConfig` — no parallel hardcoded bases.
4. **Cleartext HTTP:** Allowed only in **debug** and **profile** Android manifests (`usesCleartextTraffic=true`) so local HTTP demos work. **Release** builds keep platform default (cleartext blocked) and expect HTTPS production URLs.
5. **Makefile:** Docker targets forward `API_BASE_URL` from Make CLI → script → `--dart-define`. Make CLI overrides `.env`; empty Make value allows `.env` then the safe default.

## Alternatives considered

1. Runtime remote-config URL — rejected for this course deliverable (extra complexity; ADR-002 prefers build-time public config).
2. Global cleartext in `main` manifest — rejected (would weaken release installs).

## Consequences

- Positive: emulator demos work with HTTP; production APKs stay cleartext-blocked unless explicitly rebuilt with HTTPS.
- Negative: physical-device LAN URLs must be supplied by the developer; `localhost` inside the emulator is not the Mac host.
- Follow-up: live connectivity is a manual smoke with backend up — not claimed by this ADR alone.
