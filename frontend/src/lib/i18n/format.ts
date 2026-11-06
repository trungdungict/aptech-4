import { parseAppCurrency, type AppCurrency } from './currencies'
import { getActiveLocale } from './locale-store'
import { toIntlLocale, type AppLocale } from './locales'
import { convertMoneyAmount, resolveDisplayCurrency } from './money'

export type FormatLocaleOptions = {
  /** Defaults to the active app locale. */
  locale?: AppLocale
}

export type FormatMoneyOptions = FormatLocaleOptions & {
  /** Override display currency; defaults to active `aptech4.currency` preference. */
  displayCurrency?: AppCurrency
  /**
   * When true, skip preference conversion and format `amount` in `currency` as-is.
   * Prefer false so storefront prices respect the currency selector.
   */
  skipDisplayConversion?: boolean
  maximumFractionDigits?: number
  minimumFractionDigits?: number
}

/** Format a plain number (grouping / decimals) for the active or given locale. */
export function formatNumber(
  value: number,
  options?: Intl.NumberFormatOptions & FormatLocaleOptions,
): string {
  const { locale, ...intlOptions } = options ?? {}
  const tag = toIntlLocale(locale ?? getActiveLocale())
  if (!Number.isFinite(value)) {
    return String(value)
  }
  try {
    return new Intl.NumberFormat(tag, intlOptions).format(value)
  }
  catch {
    return String(value)
  }
}

function defaultFractionDigits(currency: string): { min: number; max: number } {
  const code = currency.toUpperCase()
  if (code === 'VND') {
    return { min: 0, max: 0 }
  }
  if (code === 'USD') {
    return { min: 2, max: 2 }
  }
  return { min: 0, max: 2 }
}

/**
 * Format money for display.
 * By default converts from the API/record `currency` into the active display
 * currency (`aptech4.currency`, default USD) using the documented mock FX rate.
 * Locale only affects number grouping / symbol placement — never the currency code.
 */
export function formatMoney(
  amount: string | number,
  currency: string,
  options?: FormatMoneyOptions,
): string {
  const numeric = typeof amount === 'number' ? amount : Number(amount)
  if (!Number.isFinite(numeric)) {
    return String(amount)
  }
  const tag = toIntlLocale(options?.locale ?? getActiveLocale())
  const source = (currency || 'USD').trim().toUpperCase() || 'USD'
  const display: AppCurrency = options?.skipDisplayConversion
    ? parseAppCurrency(source)
    : resolveDisplayCurrency(options?.displayCurrency)
  const displayAmount = options?.skipDisplayConversion
    ? numeric
    : convertMoneyAmount(numeric, source, display)
  const fractions = defaultFractionDigits(display)
  try {
    return new Intl.NumberFormat(tag, {
      style: 'currency',
      currency: display,
      minimumFractionDigits: options?.minimumFractionDigits ?? fractions.min,
      maximumFractionDigits: options?.maximumFractionDigits ?? fractions.max,
    }).format(displayAmount)
  }
  catch {
    return `${displayAmount} ${display}`
  }
}

/** Format a date/time instant for display. */
export function formatDateTime(
  value: string | number | Date,
  options?: Intl.DateTimeFormatOptions & FormatLocaleOptions,
): string {
  const { locale, ...intlOptions } = options ?? {}
  const tag = toIntlLocale(locale ?? getActiveLocale())
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  try {
    return new Intl.DateTimeFormat(tag, {
      dateStyle: 'medium',
      timeStyle: 'short',
      ...intlOptions,
    }).format(date)
  }
  catch {
    return date.toISOString()
  }
}

/** Format a calendar date (no time) for lists. */
export function formatDate(
  value: string | number | Date,
  options?: Intl.DateTimeFormatOptions & FormatLocaleOptions,
): string {
  const { locale, ...intlOptions } = options ?? {}
  const tag = toIntlLocale(locale ?? getActiveLocale())
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value)
  }
  try {
    return new Intl.DateTimeFormat(tag, {
      dateStyle: 'medium',
      ...intlOptions,
    }).format(date)
  }
  catch {
    return date.toISOString()
  }
}
