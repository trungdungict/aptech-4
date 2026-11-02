import type { AdminInventoryListParams } from '../types'

export const adminInventoryQueryKeys = {
  all: ['admin', 'inventory'] as const,
  lists: () => [...adminInventoryQueryKeys.all, 'list'] as const,
  list: (params: AdminInventoryListParams) =>
    [...adminInventoryQueryKeys.lists(), params] as const,
  summary: () => [...adminInventoryQueryKeys.all, 'summary'] as const,
}
