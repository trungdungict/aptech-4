import { useCallback, useSyncExternalStore } from 'react'

import {
  getActiveCurrency,
  parseAppCurrency,
  setActiveCurrency,
  subscribeActiveCurrency,
  type AppCurrency,
} from '@/lib/i18n'

export function useAppCurrency(): {
  currency: AppCurrency
  setCurrency: (currency: AppCurrency) => void
} {
  const currency = useSyncExternalStore(
    subscribeActiveCurrency,
    getActiveCurrency,
    () => parseAppCurrency(undefined),
  )

  const setCurrency = useCallback((next: AppCurrency) => {
    setActiveCurrency(next)
  }, [])

  return { currency, setCurrency }
}
