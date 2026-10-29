import type { OrderListParams } from '../types'

export const orderQueryKeys = {
  all: ['orders'] as const,
  lists: (userId: string) => ['orders', userId, 'list'] as const,
  list: (userId: string, params: OrderListParams) =>
    ['orders', userId, 'list', params] as const,
  details: (userId: string) => ['orders', userId, 'detail'] as const,
  detail: (userId: string, orderId: string) =>
    ['orders', userId, 'detail', orderId] as const,
}
