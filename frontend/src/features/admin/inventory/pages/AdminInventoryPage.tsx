import { useQuery } from '@tanstack/react-query'
import { PackageIcon, WarehouseIcon } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router-dom'

import { Pagination, parsePageParam, writePageParam } from '@/components/pagination'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { PRODUCT_DEFAULT_PAGE_SIZE } from '@/features/catalog/types'
import { useAppLocale } from '@/features/i18n/hooks/useAppLocale'
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
import * as inventoryApi from '../api/admin-inventory-api'
import { AdjustInventorySheet } from '../components/AdjustInventorySheet'
import { adminInventoryQueryKeys } from '../hooks/admin-inventory-query-keys'
import { stockBucketBadgeVariant, stockBucketLabelKey } from '../lib/stock-status-badge'
import {
  ADMIN_INVENTORY_DEFAULT_SORT,
  ADMIN_INVENTORY_SORT_OPTIONS,
  isAllowedAdminInventorySort,
  resolveStockBucket,
  type AdminInventoryListItem,
  type AdminInventoryStockStatusFilter,
} from '../types'

function parseStockStatus(raw: string | null): AdminInventoryStockStatusFilter {
  if (raw === 'IN_STOCK' || raw === 'LOW' || raw === 'OUT') return raw
  return 'ALL'
}

export function AdminInventoryPage() {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const { locale } = useAppLocale()
  const [searchParams, setSearchParams] = useSearchParams()
  const [adjustItem, setAdjustItem] = useState<AdminInventoryListItem | null>(null)

  const page = parsePageParam(searchParams.get('page'))
  const q = searchParams.get('q') ?? ''
  const activeParam = searchParams.get('active')
  const active =
    activeParam === 'true' ? true : activeParam === 'false' ? false : undefined
  const stockStatus = parseStockStatus(searchParams.get('stockStatus'))
  const sortParam = searchParams.get('sort') ?? ADMIN_INVENTORY_DEFAULT_SORT
  const sort = isAllowedAdminInventorySort(sortParam)
    ? sortParam
    : ADMIN_INVENTORY_DEFAULT_SORT

  const listParams = useMemo(
    () => ({
      q: q || undefined,
      active,
      stockStatus: stockStatus === 'ALL' ? undefined : stockStatus,
      page,
      size: PRODUCT_DEFAULT_PAGE_SIZE,
      sort,
    }),
    [q, active, stockStatus, page, sort],
  )

  const goToPage = (nextPage: number) => {
    setSearchParams(writePageParam(searchParams, nextPage))
  }

  const summaryQuery = useQuery({
    queryKey: adminInventoryQueryKeys.summary(),
    queryFn: ({ signal }) => inventoryApi.getAdminInventorySummary(signal),
    retry: false,
  })

  const listQuery = useQuery({
    queryKey: adminInventoryQueryKeys.list(locale, listParams),
    queryFn: ({ signal }) => inventoryApi.listAdminInventory(listParams, signal),
    retry: false,
  })

  const threshold = summaryQuery.data?.lowStockThreshold
  const data = listQuery.data

  return (
    <section className={adminPageSectionClass}>
      <div className="space-y-1">
        <h1 className={adminPageTitleClass}>{t('inventory.title')}</h1>
        <p className={adminPageLeadClass}>
          {t('inventory.lead')}
        </p>
      </div>

      {summaryQuery.isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4" role="status" aria-live="polite">
          <p className="sr-only">{t('inventory.summaryLoading')}</p>
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-24 rounded-xl" />
          ))}
        </div>
      ) : summaryQuery.isError ? (
        <div className={`space-y-3 ${adminAlertErrorClass}`}>
          <p role="alert">
            {summaryQuery.error instanceof ApiClientError
              ? summaryQuery.error.userMessage
              : t('inventory.summaryError')}
          </p>
          <Button type="button" size="sm" onClick={() => void summaryQuery.refetch()}>
            {t('inventory.retrySummary')}
          </Button>
        </div>
      ) : summaryQuery.data ? (
        <div className="space-y-2">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <SummaryStat label={t('inventory.totalProducts')} value={summaryQuery.data.totalProducts} />
            <SummaryStat label={t('inventory.inStockCount')} value={summaryQuery.data.inStockCount} />
            <SummaryStat label={t('inventory.lowStockCount')} value={summaryQuery.data.lowStockCount} />
            <SummaryStat label={t('inventory.outOfStockCount')} value={summaryQuery.data.outOfStockCount} />
          </div>
          <p className="text-xs text-muted-foreground">
            {t('inventory.thresholdBefore')}
            <span className="font-medium text-foreground">
              {summaryQuery.data.lowStockThreshold}
            </span>
            {t('inventory.thresholdAfter')}
          </p>
        </div>
      ) : null}

      <Card className="shadow-sm">
        <CardContent className="pt-6">
          <form
            className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end"
            onSubmit={(event) => {
              event.preventDefault()
              const form = new FormData(event.currentTarget)
              const next = new URLSearchParams(searchParams)
              const nextQ = String(form.get('q') ?? '').trim()
              const nextActive = String(form.get('active') ?? '')
              const nextStock = String(form.get('stockStatus') ?? 'ALL')
              const nextSort = String(form.get('sort') ?? ADMIN_INVENTORY_DEFAULT_SORT)
              if (nextQ) next.set('q', nextQ)
              else next.delete('q')
              if (nextActive === 'true' || nextActive === 'false') next.set('active', nextActive)
              else next.delete('active')
              if (nextStock === 'IN_STOCK' || nextStock === 'LOW' || nextStock === 'OUT') {
                next.set('stockStatus', nextStock)
              } else {
                next.delete('stockStatus')
              }
              if (isAllowedAdminInventorySort(nextSort) && nextSort !== ADMIN_INVENTORY_DEFAULT_SORT) {
                next.set('sort', nextSort)
              } else if (nextSort === ADMIN_INVENTORY_DEFAULT_SORT) {
                next.delete('sort')
              }
              next.delete('page')
              setSearchParams(next)
            }}
          >
            <div className="min-w-[12rem] flex-1 space-y-1.5">
              <Label htmlFor="admin-inventory-q">{t('inventory.filters.search')}</Label>
              <Input
                id="admin-inventory-q"
                name="q"
                defaultValue={q}
                placeholder={t('inventory.filters.searchPlaceholder')}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="admin-inventory-active">{t('inventory.filters.productStatus')}</Label>
              <select
                id="admin-inventory-active"
                name="active"
                defaultValue={activeParam ?? ''}
                className={adminNativeSelectClass}
              >
                <option value="">{t('inventory.filters.all')}</option>
                <option value="true">{t('common.active')}</option>
                <option value="false">{t('common.inactive')}</option>
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="admin-inventory-stock">{t('inventory.filters.stockStatus')}</Label>
              <select
                id="admin-inventory-stock"
                name="stockStatus"
                defaultValue={stockStatus}
                className={adminNativeSelectClass}
              >
                <option value="ALL">{t('inventory.filters.allStock')}</option>
                <option value="IN_STOCK">{t('inventory.stock.inStock')}</option>
                <option value="LOW">{t('inventory.stock.low')}</option>
                <option value="OUT">{t('inventory.stock.out')}</option>
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="admin-inventory-sort">{t('inventory.filters.sort')}</Label>
              <select
                id="admin-inventory-sort"
                name="sort"
                defaultValue={sort}
                className={adminNativeSelectClass}
              >
                {ADMIN_INVENTORY_SORT_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {t(option.labelKey)}
                  </option>
                ))}
              </select>
            </div>
            <Button type="submit" variant="outline">
              {t('inventory.filters.apply')}
            </Button>
          </form>
        </CardContent>
      </Card>

      {listQuery.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <p className="sr-only">{t('inventory.loading')}</p>
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-48 w-full rounded-xl" />
        </div>
      ) : listQuery.isError ? (
        <div className={`space-y-3 ${adminAlertErrorClass}`}>
          <p role="alert">
            {listQuery.error instanceof ApiClientError
              ? listQuery.error.userMessage
              : t('inventory.loadError')}
          </p>
          <Button type="button" size="sm" onClick={() => void listQuery.refetch()}>
            {tc('actions.retry')}
          </Button>
        </div>
      ) : data && data.items.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <WarehouseIcon className="size-10 text-muted-foreground opacity-60" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">{t('inventory.empty')}</p>
            <Button asChild variant="link">
              <Link to="/admin/products">{t('inventory.browseProducts')}</Link>
            </Button>
          </CardContent>
        </Card>
      ) : data ? (
        <>
          <div className={adminTableWrapClass}>
            <table className={`${adminTableClass} min-w-[52rem]`}>
              <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                <tr>
                  <th className={adminThClass}>{t('inventory.columns.product')}</th>
                  <th className={adminThClass}>{t('inventory.columns.sku')}</th>
                  <th className={adminThClass}>{t('inventory.columns.active')}</th>
                  <th className={adminThClass}>{t('inventory.columns.onHand')}</th>
                  <th className={adminThClass}>{t('inventory.columns.reserved')}</th>
                  <th className={adminThClass}>{t('inventory.columns.available')}</th>
                  <th className={adminThClass}>{t('inventory.columns.stock')}</th>
                  <th className={adminThClass}>{t('inventory.columns.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.items.map((row) => {
                  const bucket =
                    threshold == null
                      ? row.available <= 0
                        ? 'OUT'
                        : 'IN_STOCK'
                      : resolveStockBucket(row.available, threshold)
                  return (
                    <tr key={row.productId} className="hover:bg-muted/30">
                      <td className={`${adminTdClass} font-medium text-foreground`}>
                        <div className="space-y-0.5">
                          <span>{row.name}</span>
                          {!row.hasInventoryRow ? (
                            <p className="text-xs font-normal text-muted-foreground">
                              {t('inventory.noInventoryRow')}
                            </p>
                          ) : null}
                        </div>
                      </td>
                      <td className={`${adminTdClass} font-mono text-xs`}>{row.sku}</td>
                      <td className={adminTdClass}>
                        <Badge variant={row.active ? 'success' : 'warning'}>
                          {row.active ? t('common.active') : t('common.inactive')}
                        </Badge>
                      </td>
                      <td className={`${adminTdClass} tabular-nums`}>{row.quantityOnHand}</td>
                      <td className={`${adminTdClass} tabular-nums`}>{row.quantityReserved}</td>
                      <td className={`${adminTdClass} tabular-nums`}>{row.available}</td>
                      <td className={adminTdClass}>
                        <Badge variant={stockBucketBadgeVariant(bucket)}>
                          {t(stockBucketLabelKey(bucket))}
                        </Badge>
                      </td>
                      <td className={adminTdClass}>
                        <div className="flex flex-wrap gap-2">
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={() => setAdjustItem(row)}
                          >
                            {t('inventory.setOnHand')}
                          </Button>
                          <Button asChild variant="link" size="sm" className="h-auto px-0">
                            <Link to={`/admin/products/${row.productId}/edit`}>{t('inventory.editProduct')}</Link>
                          </Button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            pageSize={data.size}
            itemLabel={t('pagination.itemLabels.products')}
            disabled={listQuery.isFetching}
            onPageChange={goToPage}
          />
        </>
      ) : null}

      <AdjustInventorySheet
        item={adjustItem}
        open={adjustItem != null}
        onOpenChange={(next) => {
          if (!next) setAdjustItem(null)
        }}
      />
    </section>
  )
}

function SummaryStat({ label, value }: { label: string; value: number }) {
  return (
    <Card className="shadow-sm">
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
        <PackageIcon className="size-4 text-muted-foreground" aria-hidden="true" />
      </CardHeader>
      <CardContent>
        <p className="text-2xl font-semibold tabular-nums tracking-tight" aria-label={`${label}: ${value}`}>
          {value}
        </p>
      </CardContent>
    </Card>
  )
}
