import { CART_MAX_QUANTITY } from '../types'

/** Clamp a cart line quantity to the backend-supported range [1, CART_MAX_QUANTITY]. */
export function clampCartQuantity(value: number): number {
  if (!Number.isInteger(value)) {
    return 1
  }
  return Math.min(CART_MAX_QUANTITY, Math.max(1, value))
}
