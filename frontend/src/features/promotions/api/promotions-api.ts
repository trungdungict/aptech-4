import { apiRequest } from '@/lib/api/api-client'
import { toQueryString } from '@/lib/api/query-string'

import type { HomepagePromotions, PromotionPlacementKey, PublicPlacement } from '../types'

/** GET /api/v1/promotions/homepage — effective hero + promo slots (UTC filtered server-side). */
export async function getHomepagePromotions(signal?: AbortSignal): Promise<HomepagePromotions> {
  return apiRequest<HomepagePromotions>('/promotions/homepage', { method: 'GET', signal })
}

/** GET /api/v1/promotions/placements?keys=… — optional key filter (comma-separated). */
export async function listPromotionPlacements(
  keys?: PromotionPlacementKey[],
  signal?: AbortSignal,
): Promise<PublicPlacement[]> {
  const query = toQueryString({
    keys: keys && keys.length > 0 ? keys.join(',') : undefined,
  })
  return apiRequest<PublicPlacement[]>(`/promotions/placements${query}`, { method: 'GET', signal })
}
