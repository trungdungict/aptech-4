import {
  APP_LOCALE_STORAGE_KEY,
  DEFAULT_APP_LOCALE,
  isAppLocale,
  type AppLocale,
} from './locales'

export function readStoredAppLocale(): AppLocale {
  try {
    const raw = localStorage.getItem(APP_LOCALE_STORAGE_KEY)
    if (isAppLocale(raw)) {
      return raw
    }
  }
  catch {
    // private mode / blocked storage
  }
  return DEFAULT_APP_LOCALE
}

export function writeStoredAppLocale(locale: AppLocale): void {
  try {
    localStorage.setItem(APP_LOCALE_STORAGE_KEY, locale)
  }
  catch {
    // ignore quota / private mode
  }
}
