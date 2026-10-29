/** Matches backend `AddressResponse`. */
export type Address = {
  id: string
  label: string | null
  recipientName: string
  line1: string
  line2: string | null
  city: string
  stateRegion: string | null
  postalCode: string | null
  countryCode: string
  phone: string | null
  defaultAddress: boolean
}

/** Matches backend `AddressCreateRequest`. */
export type AddressCreatePayload = {
  label?: string
  recipientName: string
  line1: string
  line2?: string
  city: string
  stateRegion?: string
  postalCode?: string
  countryCode?: string
  phone?: string
  defaultAddress?: boolean
}

/** Matches backend `CheckoutRequest`. */
export type CheckoutPayload = {
  idempotencyKey: string
  addressId: string
  /** Only honored when backend mock force-failure is enabled. */
  simulateFailure?: boolean
}

/** Matches backend `CheckoutResponse`. */
export type CheckoutResult = {
  orderId: string
  orderStatus: string
  subtotalAmount: string | number
  shippingAmount: string | number
  totalAmount: string | number
  currency: string
  placedAt: string
  items: CheckoutOrderItem[]
  payment: CheckoutPayment
  cartCleared: boolean
}

export type CheckoutOrderItem = {
  productId: string
  productSku: string
  productName: string
  unitPrice: string | number
  quantity: number
  lineTotal: string | number
}

export type CheckoutPayment = {
  paymentId: string
  provider: string
  status: string
  amount: string | number
  currency: string
  providerReference: string | null
  failureReason: string | null
}
