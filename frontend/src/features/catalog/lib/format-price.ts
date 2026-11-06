import { formatMoney, type AppCurrency, type AppLocale } from '@/lib/i18n'

/**
 * Format product / cart / order amounts for display.
 * Uses the active currency preference (`aptech4.currency`, default USD) and
 * converts from the API currency when needed (documented mock FX).
 */
export function formatProductPrice(
  unitPrice: string | number,
  currency: string,
  locale?: AppLocale,
  displayCurrency?: AppCurrency,
): string {
  return formatMoney(unitPrice, currency, { locale, displayCurrency })
}
