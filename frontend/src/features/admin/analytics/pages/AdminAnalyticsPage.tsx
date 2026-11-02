import { useQuery } from '@tanstack/react-query'
import { RefreshCwIcon } from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ProductPrice } from '@/features/catalog/components/ProductPrice'
import { ApiClientError } from '@/lib/api/api-error'

import {
  adminAlertErrorClass,
  adminNativeSelectClass,
  adminPageLeadClass,
  adminPageSectionClass,
  adminPageTitleClass,
  adminTableClass,
  adminTableWrapClass,
  adminTdClass,
  adminThClass,
} from '../../lib/admin-ui'
import * as analyticsApi from '../api/admin-analytics-api'
import { AnalyticsSectionShell } from '../components/AnalyticsSectionShell'
import { InventorySnapshot } from '../components/InventorySnapshot'
import { RankingBars } from '../components/RankingBars'
import { StatusBars } from '../components/StatusBars'
import { TimeseriesChart } from '../components/TimeseriesChart'
import { adminAnalyticsQueryKeys } from '../hooks/admin-analytics-query-keys'
import {
  buildAppliedInstantsFromDateInputs,
  inclusiveEndFromExclusiveTo,
  instantToUtcDateInput,
  presetRangeInstants,
} from '../lib/analytics-date-range'
import {
  ADMIN_ANALYTICS_DEFAULT_LIMIT,
  ADMIN_ANALYTICS_LIMIT_OPTIONS,
  clampAnalyticsLimit,
  type AnalyticsRangeParams,
} from '../types'

function parseLimitParam(raw: string | null): number {
  if (raw == null || raw === '') return ADMIN_ANALYTICS_DEFAULT_LIMIT
  const n = Number(raw)
  return clampAnalyticsLimit(n)
}

function formatUpdatedAt(epochMs: number): string | null {
  if (!epochMs) return null
  try {
    return new Intl.DateTimeFormat('en-GB', {
      dateStyle: 'medium',
      timeStyle: 'medium',
      timeZone: 'UTC',
    }).format(new Date(epochMs))
  } catch {
    return new Date(epochMs).toISOString()
  }
}

export function AdminAnalyticsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [rangeError, setRangeError] = useState<string | null>(null)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [refreshError, setRefreshError] = useState<string | null>(null)

  const fromParam = searchParams.get('from') ?? undefined
  const toParam = searchParams.get('to') ?? undefined
  const limit = parseLimitParam(searchParams.get('limit'))

  const range: AnalyticsRangeParams = useMemo(
    () => ({
      ...(fromParam ? { from: fromParam } : {}),
      ...(toParam ? { to: toParam } : {}),
    }),
    [fromParam, toParam],
  )

  const overviewQuery = useQuery({
    queryKey: adminAnalyticsQueryKeys.overview(range),
    queryFn: ({ signal }) => analyticsApi.getAdminAnalyticsOverview(range, signal),
    retry: false,
  })

  const statusQuery = useQuery({
    queryKey: adminAnalyticsQueryKeys.status(range),
    queryFn: ({ signal }) => analyticsApi.getAdminAnalyticsOrderStatus(range, signal),
    retry: false,
  })

  const timeseriesQuery = useQuery({
    queryKey: adminAnalyticsQueryKeys.timeseries(range),
    queryFn: ({ signal }) => analyticsApi.getAdminAnalyticsOrderTimeseries(range, signal),
    retry: false,
  })

  const topQuery = useQuery({
    queryKey: adminAnalyticsQueryKeys.topProducts(range, limit),
    queryFn: ({ signal }) => analyticsApi.getAdminAnalyticsTopProducts(range, limit, signal),
    retry: false,
  })

  const categoryQuery = useQuery({
    queryKey: adminAnalyticsQueryKeys.categorySales(range, limit),
    queryFn: ({ signal }) => analyticsApi.getAdminAnalyticsCategorySales(range, limit, signal),
    retry: false,
  })

  const promotionsQuery = useQuery({
    queryKey: adminAnalyticsQueryKeys.promotions(range),
    queryFn: ({ signal }) => analyticsApi.getAdminAnalyticsPromotionSummary(range, signal),
    retry: false,
  })

  // Sync normalized Instants from overview into the URL (replace) without inventing a window.
  useEffect(() => {
    const data = overviewQuery.data
    if (!data) return
    if (searchParams.get('from') === data.from && searchParams.get('to') === data.to) {
      return
    }
    const next = new URLSearchParams(searchParams)
    next.set('from', data.from)
    next.set('to', data.to)
    setSearchParams(next, { replace: true })
  }, [overviewQuery.data, searchParams, setSearchParams])

  const appliedFromDay = fromParam ? instantToUtcDateInput(fromParam) : ''
  const appliedToInclusive = toParam ? inclusiveEndFromExclusiveTo(toParam) : ''
  const currency = overviewQuery.data?.currency ?? 'VND'

  const lastUpdatedLabel = useMemo(() => {
    const stamps = [
      overviewQuery.dataUpdatedAt,
      statusQuery.dataUpdatedAt,
      timeseriesQuery.dataUpdatedAt,
      topQuery.dataUpdatedAt,
      categoryQuery.dataUpdatedAt,
      promotionsQuery.dataUpdatedAt,
    ].filter((n) => n > 0)
    if (stamps.length === 0) return null
    return formatUpdatedAt(Math.max(...stamps))
  }, [
    overviewQuery.dataUpdatedAt,
    statusQuery.dataUpdatedAt,
    timeseriesQuery.dataUpdatedAt,
    topQuery.dataUpdatedAt,
    categoryQuery.dataUpdatedAt,
    promotionsQuery.dataUpdatedAt,
  ])

  const applyRange = (from: string, to: string) => {
    setRangeError(null)
    setRefreshError(null)
    const next = new URLSearchParams(searchParams)
    next.set('from', from)
    next.set('to', to)
    setSearchParams(next)
  }

  const onApplyDates = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const fromDay = String(form.get('fromDay') ?? '')
    const toInclusiveDay = String(form.get('toInclusiveDay') ?? '')
    const result = buildAppliedInstantsFromDateInputs(fromDay, toInclusiveDay)
    if ('error' in result) {
      setRangeError(result.error)
      return
    }
    applyRange(result.from, result.to)
  }

  const onPreset = (days: number) => {
    const { from, to } = presetRangeInstants(days)
    applyRange(from, to)
  }

  const onLimitChange = (nextLimit: number) => {
    const clamped = clampAnalyticsLimit(nextLimit)
    const next = new URLSearchParams(searchParams)
    if (clamped === ADMIN_ANALYTICS_DEFAULT_LIMIT) {
      next.delete('limit')
    } else {
      next.set('limit', String(clamped))
    }
    setSearchParams(next)
  }

  /**
   * Refetch only — do not invalidate or rewrite URL/range/limit.
   * Local isRefreshing gives reliable button state; section shells use isFetching for “Updating…”.
   */
  const refreshAll = async () => {
    if (isRefreshing) return
    setRefreshError(null)
    setIsRefreshing(true)
    try {
      const results = await Promise.all([
        overviewQuery.refetch(),
        statusQuery.refetch(),
        timeseriesQuery.refetch(),
        topQuery.refetch(),
        categoryQuery.refetch(),
        promotionsQuery.refetch(),
      ])
      const firstFailed = results.find((result) => result.isError)
      if (firstFailed?.error) {
        const message =
          firstFailed.error instanceof ApiClientError
            ? firstFailed.error.userMessage
            : firstFailed.error instanceof Error
              ? firstFailed.error.message
              : 'Refresh failed.'
        setRefreshError(message)
      }
    } catch (error) {
      setRefreshError(
        error instanceof ApiClientError
          ? error.userMessage
          : error instanceof Error
            ? error.message
            : 'Refresh failed.',
      )
    } finally {
      setIsRefreshing(false)
    }
  }

  const topRanking = (topQuery.data?.items ?? []).map((item, index) => ({
    id: `${item.productSku}-${item.productId ?? 'null'}-${index}`,
    rank: index + 1,
    title: item.productName,
    subtitle: item.productSku,
    revenue: item.revenue,
    meta: (
      <span>
        {item.unitsSold} units
        {item.productId == null ? ' · Catalog unlinked' : ''}
      </span>
    ),
    action: item.productId ? (
      <Button asChild variant="link" className="h-auto px-0 text-xs">
        <Link to={`/admin/products/${item.productId}/edit`}>Edit</Link>
      </Button>
    ) : undefined,
  }))

  const categoryRanking = (categoryQuery.data?.items ?? []).map((item, index) => ({
    id: item.categoryId,
    rank: index + 1,
    title: item.categoryName,
    revenue: item.revenue,
    meta: (
      <span>
        {item.unitsSold} units · {item.orderLineCount} lines
      </span>
    ),
  }))

  return (
    <section className={adminPageSectionClass}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className={adminPageTitleClass}>Analytics</h1>
            <Badge variant="outline">UTC</Badge>
          </div>
          <p className={`max-w-xl ${adminPageLeadClass}`}>
            Revenue statuses: PAID / PROCESSING / SHIPPED / COMPLETED. Window uses exclusive{' '}
            <code className="text-foreground">to</code>.
          </p>
          {lastUpdatedLabel ? (
            <p className="text-xs text-muted-foreground">
              Last updated:{' '}
              <time dateTime={new Date(Math.max(
                overviewQuery.dataUpdatedAt,
                statusQuery.dataUpdatedAt,
                timeseriesQuery.dataUpdatedAt,
                topQuery.dataUpdatedAt,
                categoryQuery.dataUpdatedAt,
                promotionsQuery.dataUpdatedAt,
              )).toISOString()}>
                {lastUpdatedLabel} UTC
              </time>
            </p>
          ) : null}
        </div>
        <Button
          type="button"
          variant="outline"
          onClick={() => void refreshAll()}
          disabled={isRefreshing}
          aria-busy={isRefreshing || undefined}
        >
          <RefreshCwIcon
            className={`size-4 ${isRefreshing ? 'animate-spin' : ''}`}
            aria-hidden="true"
          />
          {isRefreshing ? 'Refreshing…' : 'Refresh'}
        </Button>
      </div>

      {refreshError ? (
        <p className={adminAlertErrorClass} role="alert">
          {refreshError}
        </p>
      ) : null}

      <Card className="border-border/80 shadow-sm">
        <CardContent className="pt-5">
          <form
            key={`${fromParam ?? ''}|${toParam ?? ''}`}
            className="flex flex-col gap-3 lg:flex-row lg:flex-wrap lg:items-end"
            onSubmit={onApplyDates}
          >
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => onPreset(7)}>
                Last 7 days
              </Button>
              <Button type="button" variant="outline" size="sm" onClick={() => onPreset(30)}>
                Last 30 days
              </Button>
              <Button type="button" variant="outline" size="sm" onClick={() => onPreset(90)}>
                Last 90 days
              </Button>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="analytics-from-day">From (UTC day)</Label>
              <Input
                id="analytics-from-day"
                name="fromDay"
                type="date"
                defaultValue={appliedFromDay}
                required
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="analytics-to-day">Through (UTC day, inclusive)</Label>
              <Input
                id="analytics-to-day"
                name="toInclusiveDay"
                type="date"
                defaultValue={appliedToInclusive}
                required
              />
            </div>
            <Button type="submit">Apply</Button>
            <div className="space-y-1.5">
              <Label htmlFor="analytics-limit">Top / category limit</Label>
              <select
                id="analytics-limit"
                className={adminNativeSelectClass}
                value={limit}
                onChange={(event) => onLimitChange(Number(event.target.value))}
              >
                {ADMIN_ANALYTICS_LIMIT_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </div>
          </form>
          {rangeError ? (
            <p className={`mt-3 ${adminAlertErrorClass}`} role="alert">
              {rangeError}
            </p>
          ) : null}
          {overviewQuery.isError && overviewQuery.error instanceof ApiClientError ? (
            <p className={`mt-3 ${adminAlertErrorClass}`} role="alert">
              {overviewQuery.error.userMessage}
            </p>
          ) : null}
          {overviewQuery.data ? (
            <p className="mt-3 text-xs text-muted-foreground">
              Active window:{' '}
              <span className="font-mono text-foreground">{overviewQuery.data.from}</span>
              {' → '}
              <span className="font-mono text-foreground">{overviewQuery.data.to}</span>{' '}
              ({overviewQuery.data.timezone})
            </p>
          ) : null}
        </CardContent>
      </Card>

      <AnalyticsSectionShell
        title="Overview"
        description={overviewQuery.data?.revenueDefinition}
        isLoading={overviewQuery.isLoading}
        isFetching={overviewQuery.isFetching}
        isError={overviewQuery.isError}
        error={overviewQuery.error}
        onRetry={() => void overviewQuery.refetch()}
        skeletonHeightClass="h-36"
      >
        {overviewQuery.data ? (
          <div className="space-y-4">
            {overviewQuery.data.empty ? (
              <p className="rounded-lg border border-dashed border-border px-4 py-5 text-center text-sm text-muted-foreground">
                No orders in this range. KPI values are zero — nothing was fabricated.
              </p>
            ) : null}
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <KpiCard label="Revenue">
                <ProductPrice
                  unitPrice={overviewQuery.data.revenueTotal}
                  currency={overviewQuery.data.currency}
                  className="text-xl font-semibold tracking-tight text-foreground"
                />
                <p className="mt-1 text-xs text-muted-foreground">
                  {overviewQuery.data.revenueOrderCount} revenue orders
                </p>
              </KpiCard>
              <KpiCard label="Placed orders">
                <p className="text-xl font-semibold tabular-nums tracking-tight text-foreground">
                  {overviewQuery.data.placedOrderCount}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">All statuses in window</p>
              </KpiCard>
              <KpiCard label="Average order value">
                <ProductPrice
                  unitPrice={overviewQuery.data.averageOrderValue}
                  currency={overviewQuery.data.currency}
                  className="text-xl font-semibold tracking-tight text-foreground"
                />
              </KpiCard>
              <KpiCard label="Cancellation rate">
                <p className="text-xl font-semibold tabular-nums tracking-tight text-foreground">
                  {(overviewQuery.data.cancellationRate * 100).toFixed(1)}%
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {overviewQuery.data.cancellationCount} cancelled /{' '}
                  {overviewQuery.data.placedOrderCount} placed
                </p>
              </KpiCard>
            </div>
            <p className="text-xs text-muted-foreground">
              Attention (all dates):{' '}
              <span className="font-medium text-foreground tabular-nums">
                {overviewQuery.data.attention.totalMatching}
              </span>
              {' · '}Paid {overviewQuery.data.attention.paid} · Processing{' '}
              {overviewQuery.data.attention.processing}
            </p>
          </div>
        ) : null}
      </AnalyticsSectionShell>

      <div className="grid gap-4 xl:grid-cols-12">
        <AnalyticsSectionShell
          title="Orders & revenue over time"
          description="Daily placed-order count and revenue (UTC, zero-filled)."
          isLoading={timeseriesQuery.isLoading}
          isFetching={timeseriesQuery.isFetching}
          isError={timeseriesQuery.isError}
          error={timeseriesQuery.error}
          onRetry={() => void timeseriesQuery.refetch()}
          skeletonHeightClass="h-64"
          className="xl:col-span-7"
        >
          {timeseriesQuery.data ? (
            <TimeseriesChart
              points={timeseriesQuery.data.points}
              currency={currency}
              empty={timeseriesQuery.data.empty}
            />
          ) : null}
        </AnalyticsSectionShell>

        <div className="flex flex-col gap-4 xl:col-span-5">
          <AnalyticsSectionShell
            title="Order status"
            description="Share of total placed orders in the window."
            isLoading={statusQuery.isLoading}
            isFetching={statusQuery.isFetching}
            isError={statusQuery.isError}
            error={statusQuery.error}
            onRetry={() => void statusQuery.refetch()}
          >
            {statusQuery.data ? (
              <StatusBars
                counts={statusQuery.data.counts}
                total={statusQuery.data.total}
                empty={statusQuery.data.empty}
              />
            ) : null}
          </AnalyticsSectionShell>

          <AnalyticsSectionShell
            title="Inventory snapshot"
            description="Embedded from analytics overview (same inventory threshold)."
            isLoading={overviewQuery.isLoading}
            isFetching={overviewQuery.isFetching}
            isError={overviewQuery.isError}
            error={overviewQuery.error}
            onRetry={() => void overviewQuery.refetch()}
          >
            {overviewQuery.data ? (
              <InventorySnapshot inventory={overviewQuery.data.inventory} />
            ) : null}
          </AnalyticsSectionShell>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <AnalyticsSectionShell
          title="Top products"
          description={topQuery.data?.note}
          isLoading={topQuery.isLoading}
          isFetching={topQuery.isFetching}
          isError={topQuery.isError}
          error={topQuery.error}
          onRetry={() => void topQuery.refetch()}
        >
          {topQuery.data ? (
            <div className="space-y-3">
              <RankingBars
                items={topRanking}
                currency={currency}
                empty={topQuery.data.empty}
                emptyMessage="No product sales in this range for revenue statuses."
                ariaLabel="Top products by revenue"
              />
              {!topQuery.data.empty ? (
                <details className="rounded-lg border border-border/80">
                  <summary className="cursor-pointer px-3 py-2 text-sm font-medium">
                    Exact product table
                  </summary>
                  <div className={`${adminTableWrapClass} border-0 shadow-none`}>
                    <table className={`${adminTableClass} min-w-[28rem]`}>
                      <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                        <tr>
                          <th className={adminThClass}>#</th>
                          <th className={adminThClass}>Product</th>
                          <th className={adminThClass}>SKU</th>
                          <th className={adminThClass}>Units</th>
                          <th className={adminThClass}>Revenue</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {topQuery.data.items.map((item, index) => (
                          <tr key={`${item.productSku}-${index}`}>
                            <td className={`${adminTdClass} tabular-nums`}>{index + 1}</td>
                            <td className={`${adminTdClass} font-medium`}>
                              {item.productName}
                              {item.productId == null ? (
                                <Badge variant="warning" className="ml-2">
                                  Catalog unlinked
                                </Badge>
                              ) : null}
                            </td>
                            <td className={`${adminTdClass} font-mono text-xs`}>{item.productSku}</td>
                            <td className={`${adminTdClass} tabular-nums`}>{item.unitsSold}</td>
                            <td className={adminTdClass}>
                              <ProductPrice
                                unitPrice={item.revenue}
                                currency={currency}
                                className="text-sm font-medium text-foreground"
                              />
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </details>
              ) : null}
            </div>
          ) : null}
        </AnalyticsSectionShell>

        <AnalyticsSectionShell
          title="Category sales"
          description="Live product category attribution — historical category is not preserved."
          isLoading={categoryQuery.isLoading}
          isFetching={categoryQuery.isFetching}
          isError={categoryQuery.isError}
          error={categoryQuery.error}
          onRetry={() => void categoryQuery.refetch()}
        >
          {categoryQuery.data ? (
            <div className="space-y-3">
              <p className="text-xs text-muted-foreground">{categoryQuery.data.attributionNote}</p>
              <RankingBars
                items={categoryRanking}
                currency={currency}
                empty={categoryQuery.data.empty}
                emptyMessage="No category sales in this range."
                ariaLabel="Category sales by revenue"
              />
            </div>
          ) : null}
        </AnalyticsSectionShell>
      </div>

      <AnalyticsSectionShell
        title="Promotions"
        description="Discount totals from revenue-status orders; breakdown uses order promotion snapshots."
        isLoading={promotionsQuery.isLoading}
        isFetching={promotionsQuery.isFetching}
        isError={promotionsQuery.isError}
        error={promotionsQuery.error}
        onRetry={() => void promotionsQuery.refetch()}
      >
        {promotionsQuery.data ? (
          <div className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-3">
              <KpiCard label="Discount total">
                <ProductPrice
                  unitPrice={promotionsQuery.data.discountTotal}
                  currency={promotionsQuery.data.currency}
                  className="text-xl font-semibold text-foreground"
                />
              </KpiCard>
              <KpiCard label="Orders with promotion">
                <p className="text-xl font-semibold tabular-nums text-foreground">
                  {promotionsQuery.data.ordersWithPromotionCount}
                </p>
              </KpiCard>
              <KpiCard label="Revenue orders">
                <p className="text-xl font-semibold tabular-nums text-foreground">
                  {promotionsQuery.data.revenueOrderCount}
                </p>
              </KpiCard>
            </div>
            {promotionsQuery.data.empty ? (
              <p className="rounded-lg border border-dashed border-border px-4 py-8 text-center text-sm text-muted-foreground">
                No promotion usage in this range.
              </p>
            ) : (
              <div className={adminTableWrapClass}>
                <table className={`${adminTableClass} min-w-[48rem]`}>
                  <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                    <tr>
                      <th className={adminThClass}>Campaign</th>
                      <th className={adminThClass}>Strategy</th>
                      <th className={adminThClass}>Code</th>
                      <th className={adminThClass}>Type</th>
                      <th className={adminThClass}>Discount</th>
                      <th className={adminThClass}>Orders</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {promotionsQuery.data.breakdown.map((row) => (
                      <tr key={`${row.campaignSlug}|${row.strategyCode}`}>
                        <td className={adminTdClass}>
                          <div className="space-y-0.5">
                            <p className="font-medium">
                              {row.campaignName?.trim() || row.campaignSlug || '—'}
                            </p>
                            <p className="font-mono text-xs text-muted-foreground">
                              {row.campaignSlug}
                            </p>
                          </div>
                        </td>
                        <td className={adminTdClass}>{row.strategyName?.trim() || '—'}</td>
                        <td className={`${adminTdClass} font-mono text-xs`}>
                          {row.strategyCode ? row.strategyCode : '—'}
                        </td>
                        <td className={adminTdClass}>{row.strategyType || '—'}</td>
                        <td className={adminTdClass}>
                          <ProductPrice
                            unitPrice={row.discountTotal}
                            currency={promotionsQuery.data.currency}
                            className="text-sm font-medium text-foreground"
                          />
                        </td>
                        <td className={`${adminTdClass} tabular-nums`}>{row.orderCount}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        ) : null}
      </AnalyticsSectionShell>
    </section>
  )
}

function KpiCard({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="rounded-xl border border-border/80 bg-muted/20 px-3.5 py-3">
      <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">{label}</p>
      <div className="mt-1.5">{children}</div>
    </div>
  )
}
