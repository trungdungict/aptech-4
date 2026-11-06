import type { QueryClient } from '@tanstack/react-query'

import { adminAnalyticsQueryKeys } from '@/features/admin/analytics/hooks/admin-analytics-query-keys'
import { adminCategoryQueryKeys } from '@/features/admin/categories/hooks/admin-category-query-keys'
import { adminInventoryQueryKeys } from '@/features/admin/inventory/hooks/admin-inventory-query-keys'
import { adminProductQueryKeys } from '@/features/admin/products/hooks/admin-product-query-keys'
import { adminPromotionQueryKeys } from '@/features/admin/promotions/hooks/admin-promotion-query-keys'
import { catalogQueryKeys } from '@/features/catalog/hooks/catalog-query-keys'
import { promotionQueryKeys } from '@/features/promotions/hooks/promotion-query-keys'

export type TranslationInvalidationScope = 'product' | 'category' | 'promotion'

/**
 * Refresh locale-aware caches (their keys include the UI locale) after a translation PUT.
 * Invalidating the shared prefixes covers vi and en entries.
 */
export async function invalidateAfterTranslationSave(
  queryClient: QueryClient,
  scope: TranslationInvalidationScope,
): Promise<void> {
  const keys =
    scope === 'product'
      ? [
          adminProductQueryKeys.all,
          adminInventoryQueryKeys.all,
          catalogQueryKeys.all,
        ]
      : scope === 'category'
        ? [
            adminCategoryQueryKeys.all,
            adminProductQueryKeys.all,
            adminInventoryQueryKeys.all,
            catalogQueryKeys.all,
          ]
        : [adminPromotionQueryKeys.all, adminAnalyticsQueryKeys.all, promotionQueryKeys.all]

  await Promise.all(keys.map((queryKey) => queryClient.invalidateQueries({ queryKey })))
}
