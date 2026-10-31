import { describe, expect, it } from 'vitest'

import { CART_MAX_QUANTITY } from '../types'
import { clampCartQuantity } from './clamp-quantity'

describe('clampCartQuantity', () => {
  it('keeps values in [1, CART_MAX_QUANTITY]', () => {
    expect(clampCartQuantity(1)).toBe(1)
    expect(clampCartQuantity(0)).toBe(1)
    expect(clampCartQuantity(-3)).toBe(1)
    expect(clampCartQuantity(1.5)).toBe(1)
    expect(clampCartQuantity(CART_MAX_QUANTITY)).toBe(CART_MAX_QUANTITY)
    expect(clampCartQuantity(CART_MAX_QUANTITY + 5)).toBe(CART_MAX_QUANTITY)
  })
})
