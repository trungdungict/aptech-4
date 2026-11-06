import type { AppLocale } from '@/lib/i18n'

import type { AdminProductListParams } from '../api/admin-products-api'

/**
 * Product list/detail names are locale-resolved. Inventory quantities are invariant
 * and intentionally omit locale from the key.
 */
export const adminProductQueryKeys = {
  all: ['admin', 'products'] as const,
  lists: (locale: AppLocale) => [...adminProductQueryKeys.all, 'list', locale] as const,
  list: (locale: AppLocale, params: AdminProductListParams) =>
    [...adminProductQueryKeys.lists(locale), params] as const,
  details: (locale: AppLocale) => [...adminProductQueryKeys.all, 'detail', locale] as const,
  detail: (locale: AppLocale, id: string) =>
    [...adminProductQueryKeys.details(locale), id] as const,
  inventory: (id: string) => [...adminProductQueryKeys.all, 'inventory', id] as const,
}
