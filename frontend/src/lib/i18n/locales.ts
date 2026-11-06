/**
 * Supported UI locales (matches backend `AppLocale`).
 * Add entries here to extend the language switcher (e.g. ja, ko, th) once
 * i18n resources and backend support exist — keep handlers data-driven.
 */
export const APP_LOCALE_OPTIONS = [
  { code: 'vi', nativeLabel: 'Tiếng Việt' },
  { code: 'en', nativeLabel: 'English' },
] as const

export type AppLocale = (typeof APP_LOCALE_OPTIONS)[number]['code']

export type AppLocaleOption = (typeof APP_LOCALE_OPTIONS)[number]

/** Ordered locale codes derived from {@link APP_LOCALE_OPTIONS}. */
export const APP_LOCALES: readonly AppLocale[] = APP_LOCALE_OPTIONS.map((option) => option.code)

/** Default and missing/invalid fallback — matches backend T02. */
export const DEFAULT_APP_LOCALE: AppLocale = 'vi'

export const APP_LOCALE_STORAGE_KEY = 'aptech4.locale'

/**
 * BCP-47 tags for `Intl` formatting.
 * - `vi` → `vi-VN`
 * - `en` → `en-US` (frozen for P34)
 * Currency preference is independent (`aptech4.currency`); locale only affects grouping/date style.
 */
export function toIntlLocale(locale: AppLocale): string {
  return locale === 'en' ? 'en-US' : 'vi-VN'
}

export function isAppLocale(value: unknown): value is AppLocale {
  return typeof value === 'string' && (APP_LOCALES as readonly string[]).includes(value)
}

export function parseAppLocale(value: unknown): AppLocale {
  return isAppLocale(value) ? value : DEFAULT_APP_LOCALE
}

export function getAppLocaleOption(locale: AppLocale): AppLocaleOption {
  const option = APP_LOCALE_OPTIONS.find((entry) => entry.code === locale)
  return option ?? APP_LOCALE_OPTIONS[0]
}
