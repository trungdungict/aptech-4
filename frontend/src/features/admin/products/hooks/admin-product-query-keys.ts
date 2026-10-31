import type { AdminProductListParams } from '../api/admin-products-api'

export const adminProductQueryKeys = {
  all: ['admin', 'products'] as const,
  lists: () => [...adminProductQueryKeys.all, 'list'] as const,
  list: (params: AdminProductListParams) => [...adminProductQueryKeys.lists(), params] as const,
  details: () => [...adminProductQueryKeys.all, 'detail'] as const,
  detail: (id: string) => [...adminProductQueryKeys.details(), id] as const,
  inventory: (id: string) => [...adminProductQueryKeys.all, 'inventory', id] as const,
}
