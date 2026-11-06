/**
 * Supported storefront display currencies.
 * Catalog / cart / order amounts are stored in USD (currency of record).
 * VND is display-only via the documented mock FX rate — not live FX.
 *
 * Add entries here to extend the currency switcher once formatting + rate
 * policy exist — keep handlers data-driven.
 */
export const APP_CURRENCY_OPTIONS = [
  { code: 'USD', label: 'USD', symbolHint: '$' },
  { code: 'VND', label: 'VND', symbolHint: '₫' },
] as const

export type AppCurrency = (typeof APP_CURRENCY_OPTIONS)[number]['code']

export type AppCurrencyOption = (typeof APP_CURRENCY_OPTIONS)[number]

/** Ordered currency codes derived from {@link APP_CURRENCY_OPTIONS}. */
export const APP_CURRENCIES: readonly AppCurrency[] = APP_CURRENCY_OPTIONS.map(
  (option) => option.code,
)

/** Default when no preference is stored. */
export const DEFAULT_APP_CURRENCY: AppCurrency = 'USD'

export const APP_CURRENCY_STORAGE_KEY = 'aptech4.currency'

/**
 * Deterministic demo FX: 1 USD = 25_000 VND.
 * Used only for storefront display conversion — never live rates.
 */
export const MOCK_USD_TO_VND_RATE = 25_000

/** Backend / seed currency of record for FoodMart demo amounts. */
export const RECORD_CURRENCY: AppCurrency = 'USD'

export function isAppCurrency(value: unknown): value is AppCurrency {
  return typeof value === 'string' && (APP_CURRENCIES as readonly string[]).includes(value)
}

export function parseAppCurrency(value: unknown): AppCurrency {
  return isAppCurrency(value) ? value : DEFAULT_APP_CURRENCY
}

export function getAppCurrencyOption(currency: AppCurrency): AppCurrencyOption {
  const option = APP_CURRENCY_OPTIONS.find((entry) => entry.code === currency)
  return option ?? APP_CURRENCY_OPTIONS[0]
}
