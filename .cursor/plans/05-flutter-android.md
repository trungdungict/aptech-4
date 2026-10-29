# P11–P12 — Flutter Android customer app and APK

## Objective

Flutter customer app using same API; configurable base URL; release APK via Makefile.

## Prerequisites

- Auth/catalog/cart/order APIs stable enough for client
- Flutter SDK installed (or mark `BLOCKED`)

## P11 — App

- Riverpod, GoRouter, Dio, secure storage for tokens
- Screens: login/register, catalog, detail, cart, checkout, orders

## P12 — Config + APK

- Build-time `API_BASE_URL` (dart-define or equivalent)
- Docs: emulator `10.0.2.2`, device LAN IP, EC2 public URL
- `make apk API_BASE_URL=...` produces release APK path recorded as evidence

## Acceptance criteria

- Widget/unit tests for key widgets/providers
- Analyzer clean for app code
- Release APK built with explicit URL (when Flutter available)

## Verification

```bash
cd mobile && flutter test && flutter analyze
make apk API_BASE_URL=http://10.0.2.2:8080
```

## Risks

- Flutter not installed on assessment host → install or Docker; keep tasks `BLOCKED` until verified

## Phase complete when

P11–P12 verified or remaining items explicitly blocked with install instructions.
