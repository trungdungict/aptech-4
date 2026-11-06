# CURRENT_SESSION

Last updated: 2026-11-03

## Session goal

Refactor storefront language switcher to compact dropdown — **DONE**.

## Acceptance criteria

- Compact globe + current label + chevron dropdown (VI/EN native names)
- Preserve `aptech4.locale`, i18next, Accept-Language, persistence
- Extensible locale options; accessible menu; tests updated

## Completed this session

- `LanguageSwitcher` → Radix dropdown; `APP_LOCALE_OPTIONS` in `locales.ts`
- Added `dropdown-menu` UI primitive; LanguageSwitcher unit tests
- Updated bilingual E2E helpers; rebuilt Compose `web` only
- typecheck / lint / build / vitest **333/333** / e2e **7/7**

## In progress

—

## Do not

- Commit/push unless asked
- Change backend / order snapshots / chat selector
- Destructive commands / `make down-all`
