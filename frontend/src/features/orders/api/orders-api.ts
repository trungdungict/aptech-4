import { apiRequest } from '@/lib/api/api-client'
import { toQueryString } from '@/lib/api/query-string'

import type { Order, OrderListParams, OrderPage } from '../types'
import { ORDER_DEFAULT_PAGE_SIZE, ORDER_DEFAULT_SORT } from '../types'

export function listOrders(
  params: OrderListParams = {},
  signal?: AbortSignal,
): Promise<OrderPage> {
  const query = toQueryString({
    status: params.status,
    page: params.page ?? 0,
    size: params.size ?? ORDER_DEFAULT_PAGE_SIZE,
    sort: params.sort ?? ORDER_DEFAULT_SORT,
  })
  return apiRequest<OrderPage>(`/orders${query}`, { method: 'GET', auth: true, signal })
}

export function getOrder(orderId: string, signal?: AbortSignal): Promise<Order> {
  return apiRequest<Order>(`/orders/${orderId}`, { method: 'GET', auth: true, signal })
}

export function cancelOrder(orderId: string, signal?: AbortSignal): Promise<Order> {
  return apiRequest<Order>(`/orders/${orderId}/cancel`, {
    method: 'POST',
    auth: true,
    signal,
  })
}
