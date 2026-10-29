import type { PageResponse } from '@/features/catalog/types'

/** Matches backend `OrderResponse`. */
export type Order = {
  id: string
  userId: string
  status: string
  subtotalAmount: string | number
  shippingAmount: string | number
  totalAmount: string | number
  currency: string
  placedAt: string
  updatedAt: string
  shipping: OrderShipping
  items: OrderItem[]
  payment: OrderPaymentSummary | null
}

export type OrderShipping = {
  recipientName: string
  line1: string
  line2: string | null
  city: string
  stateRegion: string | null
  postalCode: string | null
  countryCode: string
  phone: string | null
}

export type OrderItem = {
  productId: string
  productSku: string
  productName: string
  unitPrice: string | number
  quantity: number
  lineTotal: string | number
}

export type OrderPaymentSummary = {
  paymentId: string
  provider: string
  status: string
  amount: string | number
  currency: string
  providerReference: string | null
  failureReason: string | null
}

export type OrderListParams = {
  status?: string
  page?: number
  size?: number
  sort?: string
}

export type OrderPage = PageResponse<Order>

export const ORDER_DEFAULT_PAGE_SIZE = 20
export const ORDER_DEFAULT_SORT = 'placedAt,desc'
