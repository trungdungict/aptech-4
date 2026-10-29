import type { AdminOrderListParams } from '../api/admin-orders-api'

export const adminOrderQueryKeys = {
  all: ['admin', 'orders'] as const,
  lists: () => [...adminOrderQueryKeys.all, 'list'] as const,
  list: (params: AdminOrderListParams) => [...adminOrderQueryKeys.lists(), params] as const,
  details: () => [...adminOrderQueryKeys.all, 'detail'] as const,
  detail: (orderId: string) => [...adminOrderQueryKeys.details(), orderId] as const,
}
