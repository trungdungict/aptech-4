import { apiRequest } from '@/lib/api/api-client'
import { toQueryString } from '@/lib/api/query-string'

import {
  clampAnalyticsLimit,
  type AdminAnalyticsCategorySales,
  type AdminAnalyticsOverview,
  type AdminAnalyticsPromotionSummary,
  type AdminAnalyticsStatus,
  type AdminAnalyticsTimeseries,
  type AdminAnalyticsTopProducts,
  type AnalyticsRangeParams,
} from '../types'

function rangeQuery(range: AnalyticsRangeParams, extra?: Record<string, string | number | undefined>) {
  return toQueryString({
    from: range.from,
    to: range.to,
    ...extra,
  })
}

/** GET /api/v1/admin/analytics/overview */
export function getAdminAnalyticsOverview(
  range: AnalyticsRangeParams = {},
  signal?: AbortSignal,
): Promise<AdminAnalyticsOverview> {
  return apiRequest<AdminAnalyticsOverview>(`/admin/analytics/overview${rangeQuery(range)}`, {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** GET /api/v1/admin/analytics/orders/status */
export function getAdminAnalyticsOrderStatus(
  range: AnalyticsRangeParams = {},
  signal?: AbortSignal,
): Promise<AdminAnalyticsStatus> {
  return apiRequest<AdminAnalyticsStatus>(`/admin/analytics/orders/status${rangeQuery(range)}`, {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** GET /api/v1/admin/analytics/orders/timeseries */
export function getAdminAnalyticsOrderTimeseries(
  range: AnalyticsRangeParams = {},
  signal?: AbortSignal,
): Promise<AdminAnalyticsTimeseries> {
  return apiRequest<AdminAnalyticsTimeseries>(
    `/admin/analytics/orders/timeseries${rangeQuery(range)}`,
    { method: 'GET', auth: true, signal },
  )
}

/** GET /api/v1/admin/analytics/products/top */
export function getAdminAnalyticsTopProducts(
  range: AnalyticsRangeParams = {},
  limit = 10,
  signal?: AbortSignal,
): Promise<AdminAnalyticsTopProducts> {
  return apiRequest<AdminAnalyticsTopProducts>(
    `/admin/analytics/products/top${rangeQuery(range, { limit: clampAnalyticsLimit(limit) })}`,
    { method: 'GET', auth: true, signal },
  )
}

/** GET /api/v1/admin/analytics/categories/sales */
export function getAdminAnalyticsCategorySales(
  range: AnalyticsRangeParams = {},
  limit = 10,
  signal?: AbortSignal,
): Promise<AdminAnalyticsCategorySales> {
  return apiRequest<AdminAnalyticsCategorySales>(
    `/admin/analytics/categories/sales${rangeQuery(range, { limit: clampAnalyticsLimit(limit) })}`,
    { method: 'GET', auth: true, signal },
  )
}

/** GET /api/v1/admin/analytics/promotions/summary */
export function getAdminAnalyticsPromotionSummary(
  range: AnalyticsRangeParams = {},
  signal?: AbortSignal,
): Promise<AdminAnalyticsPromotionSummary> {
  return apiRequest<AdminAnalyticsPromotionSummary>(
    `/admin/analytics/promotions/summary${rangeQuery(range)}`,
    { method: 'GET', auth: true, signal },
  )
}
