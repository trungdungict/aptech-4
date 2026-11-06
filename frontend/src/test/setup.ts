import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeAll, beforeEach } from 'vitest'

import {
  APP_CURRENCY_STORAGE_KEY,
  APP_LOCALE_STORAGE_KEY,
  changeAppLocale,
  DEFAULT_APP_CURRENCY,
  hydrateActiveCurrencyFromStorage,
  initI18n,
  setActiveCurrency,
} from '@/lib/i18n'

beforeAll(async () => {
  await initI18n()
})

/**
 * Customer UI tests assert English copy by default (app default is VI).
 * Use `await changeAppLocale('vi')` inside a test to cover Vietnamese.
 * Currency preference defaults to USD and is independent of locale.
 */
beforeEach(async () => {
  await changeAppLocale('en')
  // Keep storage clean so tests asserting default/persistence behavior are unaffected.
  localStorage.removeItem(APP_LOCALE_STORAGE_KEY)
  localStorage.removeItem(APP_CURRENCY_STORAGE_KEY)
  setActiveCurrency(DEFAULT_APP_CURRENCY)
  hydrateActiveCurrencyFromStorage()
})

afterEach(() => {
  cleanup()
})
