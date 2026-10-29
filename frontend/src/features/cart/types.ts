/** Matches backend `CartItemResponse`. */
export type CartItem = {
  id: string
  productId: string
  productName: string
  imageUrl: string | null
  quantity: number
  unitPrice: string | number
  lineSubtotal: string | number
  currency: string
  available: boolean
  productActive: boolean
}

/** Matches backend `CartResponse`. */
export type Cart = {
  id: string
  items: CartItem[]
  subtotal: string | number
  currency: string
  hasUnavailableItems: boolean
}

export type AddCartItemPayload = {
  productId: string
  quantity: number
}

export type UpdateCartItemPayload = {
  quantity: number
}

/** Backend CartConstraints.MAX_QUANTITY */
export const CART_MAX_QUANTITY = 99
