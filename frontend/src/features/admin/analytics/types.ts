/** Mirrors backend AdminInventorySummaryResponse (embedded in overview). */
export type AdminAnalyticsInventorySummary = {
  totalProducts: number
  inStockCount: number
  lowStockCount: number
  outOfStockCount: number
  lowStockThreshold: number
  availableDefinition: string
}

/** Mirrors AdminAnalyticsAttentionResponse. */
export type AdminAnalyticsAttention = {
  paid: number
  processing: number
  totalMatching: number
}

/** Mirrors AdminAnalyticsOverviewResponse. */
export type AdminAnalyticsOverview = {
  from: string
  to: string
  timezone: string
  currency: string
  revenueStatuses: string[]
  placedOrderCount: number
  revenueOrderCount: number
  revenueTotal: string | number
  averageOrderValue: string | number
  cancellationCount: number
  cancellationRate: number
  statusCounts: Record<string, number>
  attention: AdminAnalyticsAttention
  inventory: AdminAnalyticsInventorySummary
  empty: boolean
  revenueDefinition: string
}

/** Mirrors AdminAnalyticsStatusResponse. */
export type AdminAnalyticsStatus = {
  from: string
  to: string
  timezone: string
  counts: Record<string, number>
  total: number
  empty: boolean
}

/** Mirrors AdminAnalyticsTimeseriesPointResponse. */
export type AdminAnalyticsTimeseriesPoint = {
  date: string
  placedOrderCount: number
  revenueTotal: string | number
}

/** Mirrors AdminAnalyticsTimeseriesResponse. */
export type AdminAnalyticsTimeseries = {
  from: string
  to: string
  timezone: string
  bucket: string
  points: AdminAnalyticsTimeseriesPoint[]
  empty: boolean
}

/** Mirrors AdminAnalyticsTopProductItemResponse. */
export type AdminAnalyticsTopProductItem = {
  productSku: string
  productName: string
  productId: string | null
  unitsSold: number
  revenue: string | number
  path: string | null
}

/** Mirrors AdminAnalyticsTopProductsResponse. */
export type AdminAnalyticsTopProducts = {
  from: string
  to: string
  timezone: string
  revenueStatuses: string[]
  items: AdminAnalyticsTopProductItem[]
  empty: boolean
  note: string
}

/** Mirrors AdminAnalyticsCategorySalesItemResponse. */
export type AdminAnalyticsCategorySalesItem = {
  categoryId: string
  categoryName: string
  unitsSold: number
  revenue: string | number
  orderLineCount: number
}

/** Mirrors AdminAnalyticsCategorySalesResponse. */
export type AdminAnalyticsCategorySales = {
  from: string
  to: string
  timezone: string
  revenueStatuses: string[]
  items: AdminAnalyticsCategorySalesItem[]
  empty: boolean
  attributionNote: string
}

/** Mirrors AdminAnalyticsPromotionBreakdownItemResponse. */
export type AdminAnalyticsPromotionBreakdownItem = {
  campaignSlug: string
  strategyCode: string
  campaignName: string
  strategyName: string
  strategyType: string
  discountTotal: string | number
  orderCount: number
}

/** Mirrors AdminAnalyticsPromotionSummaryResponse. */
export type AdminAnalyticsPromotionSummary = {
  from: string
  to: string
  timezone: string
  currency: string
  revenueStatuses: string[]
  discountTotal: string | number
  ordersWithPromotionCount: number
  revenueOrderCount: number
  breakdown: AdminAnalyticsPromotionBreakdownItem[]
  empty: boolean
}

export type AnalyticsRangeParams = {
  from?: string
  to?: string
}

export const ADMIN_ANALYTICS_DEFAULT_LIMIT = 10
export const ADMIN_ANALYTICS_MAX_LIMIT = 20
export const ADMIN_ANALYTICS_LIMIT_OPTIONS = [5, 10, 20] as const

/** Display order matching backend OrderStatus enum. */
export const ORDER_STATUS_DISPLAY_ORDER = [
  'PENDING_PAYMENT',
  'PAID',
  'PROCESSING',
  'SHIPPED',
  'COMPLETED',
  'CANCELLED',
  'FAILED',
] as const

export function clampAnalyticsLimit(limit: number): number {
  if (!Number.isFinite(limit) || limit < 1) {
    return ADMIN_ANALYTICS_DEFAULT_LIMIT
  }
  return Math.min(Math.max(Math.trunc(limit), 1), ADMIN_ANALYTICS_MAX_LIMIT)
}
