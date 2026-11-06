import {
  MOCK_USD_TO_VND_RATE,
  RECORD_CURRENCY,
  parseAppCurrency,
  type AppCurrency,
} from './currencies'
import { getActiveCurrency } from './currency-store'

/**
 * Convert an amount between USD (record) and VND (display) using the
 * documented mock rate. Same-currency and unknown pairs pass through.
 */
export function convertMoneyAmount(
  amount: number,
  fromCurrency: string,
  toCurrency: AppCurrency,
): number {
  if (!Number.isFinite(amount)) {
    return amount
  }
  const from = parseAppCurrency(fromCurrency || RECORD_CURRENCY)
  const to = parseAppCurrency(toCurrency)
  if (from === to) {
    return amount
  }
  if (from === 'USD' && to === 'VND') {
    return amount * MOCK_USD_TO_VND_RATE
  }
  if (from === 'VND' && to === 'USD') {
    return amount / MOCK_USD_TO_VND_RATE
  }
  return amount
}

/** Resolve preferred display currency (explicit override or active preference). */
export function resolveDisplayCurrency(preferred?: AppCurrency): AppCurrency {
  return preferred ?? getActiveCurrency()
}
