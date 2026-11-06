import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import { DEFAULT_APP_LOCALE, type AppLocale } from './locales'
import { setActiveLocale } from './locale-store'
import { APP_NAMESPACES, resources } from './resources'
import { readStoredAppLocale, writeStoredAppLocale } from './storage'

declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'common'
    resources: (typeof resources)['vi']
  }
}

function applyDocumentLang(locale: AppLocale): void {
  if (typeof document !== 'undefined') {
    document.documentElement.lang = locale
  }
}

/**
 * Initialize i18next once (SPA). Default + missing-key fallback is Vietnamese.
 * Call before `createRoot` so the first paint uses the persisted locale.
 */
export async function initI18n(): Promise<typeof i18n> {
  const initial = readStoredAppLocale()
  setActiveLocale(initial)
  applyDocumentLang(initial)

  if (!i18n.isInitialized) {
    await i18n.use(initReactI18next).init({
      resources,
      lng: initial,
      fallbackLng: DEFAULT_APP_LOCALE,
      supportedLngs: ['vi', 'en'],
      defaultNS: 'common',
      ns: [...APP_NAMESPACES],
      interpolation: { escapeValue: false },
      returnNull: false,
      // Prefer VI resource when a key is missing in EN.
      nonExplicitSupportedLngs: false,
    })
  }
  else if (i18n.language !== initial) {
    await i18n.changeLanguage(initial)
  }

  return i18n
}

/** Change UI locale, persist, sync API locale store + `document.documentElement.lang`. */
export async function changeAppLocale(locale: AppLocale): Promise<void> {
  setActiveLocale(locale)
  writeStoredAppLocale(locale)
  applyDocumentLang(locale)
  await i18n.changeLanguage(locale)
}

export { i18n }
