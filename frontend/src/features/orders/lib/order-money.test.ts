import { describe, expect, it } from 'vitest'

import { hasOrderDiscount, toMoneyNumber } from './order-money'

describe('order-money', () => {
  it('maps API money fields without inventing totals', () => {
    expect(toMoneyNumber(0)).toBe(0)
    expect(toMoneyNumber('12.50')).toBe(12.5)
    expect(toMoneyNumber(null)).toBe(0)
    expect(toMoneyNumber(undefined)).toBe(0)
    expect(toMoneyNumber('')).toBe(0)
  })

  it('treats only positive discountAmount as a visible discount', () => {
    expect(hasOrderDiscount(0)).toBe(false)
    expect(hasOrderDiscount('0')).toBe(false)
    expect(hasOrderDiscount(0.01)).toBe(true)
    expect(hasOrderDiscount('1000')).toBe(true)
  })
})
