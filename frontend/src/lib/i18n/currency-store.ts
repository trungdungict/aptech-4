import {
  DEFAULT_APP_CURRENCY,
  parseAppCurrency,
  type AppCurrency,
} from './currencies'
import { readStoredAppCurrency, writeStoredAppCurrency } from './currency-storage'

/**
 * Mutable display-currency store (independent of locale).
 * Components subscribe via {@link subscribeActiveCurrency} / `useSyncExternalStore`.
 */
let activeCurrency: AppCurrency = DEFAULT_APP_CURRENCY
const listeners = new Set<() => void>()

/** Hydrate from localStorage once (browser only). */
export function hydrateActiveCurrencyFromStorage(): void {
  activeCurrency = readStoredAppCurrency()
  emit()
}

export function getActiveCurrency(): AppCurrency {
  return activeCurrency
}

export function setActiveCurrency(currency: AppCurrency): void {
  const next = parseAppCurrency(currency)
  if (next === activeCurrency) {
    return
  }
  activeCurrency = next
  writeStoredAppCurrency(next)
  emit()
}

export function subscribeActiveCurrency(onStoreChange: () => void): () => void {
  listeners.add(onStoreChange)
  return () => {
    listeners.delete(onStoreChange)
  }
}

function emit(): void {
  for (const listener of listeners) {
    listener()
  }
}
