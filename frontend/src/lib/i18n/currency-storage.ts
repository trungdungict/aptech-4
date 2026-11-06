import {
  APP_CURRENCY_STORAGE_KEY,
  DEFAULT_APP_CURRENCY,
  isAppCurrency,
  type AppCurrency,
} from './currencies'

export function readStoredAppCurrency(): AppCurrency {
  try {
    const raw = localStorage.getItem(APP_CURRENCY_STORAGE_KEY)
    if (isAppCurrency(raw)) {
      return raw
    }
  }
  catch {
    // private mode / blocked storage
  }
  return DEFAULT_APP_CURRENCY
}

export function writeStoredAppCurrency(currency: AppCurrency): void {
  try {
    localStorage.setItem(APP_CURRENCY_STORAGE_KEY, currency)
  }
  catch {
    // ignore quota / private mode
  }
}
