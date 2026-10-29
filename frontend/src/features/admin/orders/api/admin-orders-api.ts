import { apiRequest } from '@/lib/api/api-client'
import { toQueryString } from '@/lib/api/query-string'
import {
  ORDER_DEFAULT_PAGE_SIZE,
  ORDER_DEFAULT_SORT,
  type Order,
  type OrderListParams,
  type OrderPage,
} from '@/features/orders/types'

export type AdminOrderListParams = OrderListParams

export type AdminOrderStatusPayload = {
  status: string
}

const ALLOWED_SORT = new Set([
  'placedAt,asc',
  'placedAt,desc',
  'totalAmount,asc',
  'totalAmount,desc',
  'status,asc',
  'status,desc',
])

function normalizeListParams(params: AdminOrderListParams): AdminOrderListParams {
  const page = params.page ?? 0
  const size = Math.min(Math.max(params.size ?? ORDER_DEFAULT_PAGE_SIZE, 1), 100)
  const sort =
    params.sort && ALLOWED_SORT.has(params.sort) ? params.sort : ORDER_DEFAULT_SORT

  return {
    status: params.status || undefined,
    page: page < 0 ? 0 : page,
    size,
    sort,
  }
}

/** GET /api/v1/admin/orders — all customers; optional status filter. */
export function listAdminOrders(
  params: AdminOrderListParams = {},
  signal?: AbortSignal,
): Promise<OrderPage> {
  const normalized = normalizeListParams(params)
  const query = toQueryString({
    status: normalized.status,
    page: normalized.page,
    size: normalized.size,
    sort: normalized.sort,
  })
  return apiRequest<OrderPage>(`/admin/orders${query}`, {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** GET /api/v1/admin/orders/{orderId} */
export function getAdminOrder(orderId: string, signal?: AbortSignal): Promise<Order> {
  return apiRequest<Order>(`/admin/orders/${encodeURIComponent(orderId)}`, {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** PATCH /api/v1/admin/orders/{orderId}/status — fulfillment transitions only. */
export function updateAdminOrderStatus(
  orderId: string,
  payload: AdminOrderStatusPayload,
  signal?: AbortSignal,
): Promise<Order> {
  return apiRequest<Order>(`/admin/orders/${encodeURIComponent(orderId)}/status`, {
    method: 'PATCH',
    auth: true,
    body: { status: payload.status },
    signal,
  })
}
