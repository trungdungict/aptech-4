import type { AppLocale } from '@/lib/i18n'

import type { AdminInventoryListParams } from '../types'

export const adminInventoryQueryKeys = {
  all: ['admin', 'inventory'] as const,
  /** Product names in rows are locale-resolved, so locale is part of the list key. */
  lists: (locale: AppLocale) => [...adminInventoryQueryKeys.all, 'list', locale] as const,
  list: (locale: AppLocale, params: AdminInventoryListParams) =>
    [...adminInventoryQueryKeys.lists(locale), params] as const,
  summary: () => [...adminInventoryQueryKeys.all, 'summary'] as const,
}
