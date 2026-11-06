import {
  DEFAULT_APP_LOCALE,
  parseAppLocale,
  type AppLocale,
} from './locales'

/**
 * Mutable active-locale store shared by i18n init and the API client.
 * Intentionally does **not** import i18next — avoids circular deps with `api-client`.
 */
let activeLocale: AppLocale = DEFAULT_APP_LOCALE

export function getActiveLocale(): AppLocale {
  return activeLocale
}

export function setActiveLocale(locale: AppLocale): void {
  activeLocale = parseAppLocale(locale)
}
