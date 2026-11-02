import { apiRequest } from '@/lib/api/api-client'
import { toQueryString } from '@/lib/api/query-string'
import type { PageResponse } from '@/features/catalog/types'
import { PRODUCT_DEFAULT_PAGE_SIZE, PRODUCT_MAX_PAGE_SIZE } from '@/features/catalog/types'

import {
  ADMIN_INVENTORY_DEFAULT_SORT,
  isAllowedAdminInventorySort,
  type AdminInventoryListItem,
  type AdminInventoryListParams,
  type AdminInventorySummary,
} from '../types'

export type { AdminInventoryListItem, AdminInventoryListParams, AdminInventorySummary }

function normalizeListParams(params: AdminInventoryListParams): AdminInventoryListParams {
  const page = params.page ?? 0
  const size = params.size ?? PRODUCT_DEFAULT_PAGE_SIZE
  const sort =
    params.sort && isAllowedAdminInventorySort(params.sort)
      ? params.sort
      : ADMIN_INVENTORY_DEFAULT_SORT
  const stockStatus =
    params.stockStatus && params.stockStatus !== 'ALL' ? params.stockStatus : undefined

  return {
    q: params.q?.trim() || undefined,
    active: params.active,
    stockStatus,
    page: page < 0 ? 0 : page,
    size: Math.min(Math.max(size, 1), PRODUCT_MAX_PAGE_SIZE),
    sort,
  }
}

/** GET /api/v1/admin/inventory */
export function listAdminInventory(
  params: AdminInventoryListParams = {},
  signal?: AbortSignal,
): Promise<PageResponse<AdminInventoryListItem>> {
  const normalized = normalizeListParams(params)
  const query = toQueryString({
    q: normalized.q,
    active: normalized.active,
    stockStatus: normalized.stockStatus,
    page: normalized.page,
    size: normalized.size,
    sort: normalized.sort,
  })
  return apiRequest<PageResponse<AdminInventoryListItem>>(`/admin/inventory${query}`, {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** GET /api/v1/admin/inventory/summary */
export function getAdminInventorySummary(signal?: AbortSignal): Promise<AdminInventorySummary> {
  return apiRequest<AdminInventorySummary>('/admin/inventory/summary', {
    method: 'GET',
    auth: true,
    signal,
  })
}
