import type { AnalyticsRangeParams } from '../types'

export const adminAnalyticsQueryKeys = {
  all: ['admin', 'analytics'] as const,
  overview: (range: AnalyticsRangeParams) =>
    [...adminAnalyticsQueryKeys.all, 'overview', range] as const,
  status: (range: AnalyticsRangeParams) =>
    [...adminAnalyticsQueryKeys.all, 'status', range] as const,
  timeseries: (range: AnalyticsRangeParams) =>
    [...adminAnalyticsQueryKeys.all, 'timeseries', range] as const,
  topProducts: (range: AnalyticsRangeParams, limit: number) =>
    [...adminAnalyticsQueryKeys.all, 'topProducts', range, limit] as const,
  categorySales: (range: AnalyticsRangeParams, limit: number) =>
    [...adminAnalyticsQueryKeys.all, 'categorySales', range, limit] as const,
  promotions: (range: AnalyticsRangeParams) =>
    [...adminAnalyticsQueryKeys.all, 'promotions', range] as const,
}
