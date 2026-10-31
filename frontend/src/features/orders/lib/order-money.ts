/** Coerce API money fields for display comparisons only — never recalculate discounts. */
export function toMoneyNumber(value: string | number | null | undefined): number {
  if (value == null || value === '') {
    return 0
  }
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? n : 0
}

export function hasOrderDiscount(discountAmount: string | number | null | undefined): boolean {
  return toMoneyNumber(discountAmount) > 0
}
