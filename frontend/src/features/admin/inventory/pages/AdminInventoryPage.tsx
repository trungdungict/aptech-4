import { useQuery } from '@tanstack/react-query'
import { PackageIcon, WarehouseIcon } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { Pagination, parsePageParam, writePageParam } from '@/components/pagination'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { PRODUCT_DEFAULT_PAGE_SIZE } from '@/features/catalog/types'
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
import { stockBucketBadgeVariant, stockBucketLabel } from '../lib/stock-status-badge'
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
    queryKey: adminInventoryQueryKeys.list(listParams),
    queryFn: ({ signal }) => inventoryApi.listAdminInventory(listParams, signal),
    retry: false,
  })

  const threshold = summaryQuery.data?.lowStockThreshold
  const data = listQuery.data

  return (
    <section className={adminPageSectionClass}>
      <div className="space-y-1">
        <h1 className={adminPageTitleClass}>Inventory</h1>
        <p className={adminPageLeadClass}>
          Catalog-wide stock (active and inactive). Available = on hand − reserved. Adjustments set
          absolute on-hand only; reserved units come from open checkouts.
        </p>
      </div>

      {summaryQuery.isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4" role="status" aria-live="polite">
          <p className="sr-only">Loading inventory summary…</p>
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-24 rounded-xl" />
          ))}
        </div>
      ) : summaryQuery.isError ? (
        <div className={`space-y-3 ${adminAlertErrorClass}`}>
          <p role="alert">
            {summaryQuery.error instanceof ApiClientError
              ? summaryQuery.error.userMessage
              : 'Unable to load inventory summary.'}
          </p>
          <Button type="button" size="sm" onClick={() => void summaryQuery.refetch()}>
            Retry summary
          </Button>
        </div>
      ) : summaryQuery.data ? (
        <div className="space-y-2">
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <SummaryStat label="Total products" value={summaryQuery.data.totalProducts} />
            <SummaryStat label="In stock" value={summaryQuery.data.inStockCount} />
            <SummaryStat label="Low stock" value={summaryQuery.data.lowStockCount} />
            <SummaryStat label="Out of stock" value={summaryQuery.data.outOfStockCount} />
          </div>
          <p className="text-xs text-muted-foreground">
            Low-stock threshold is configured on the server (
            <span className="font-medium text-foreground">
              {summaryQuery.data.lowStockThreshold}
            </span>
            ). LOW means available is greater than 0 and at most that threshold.
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
              <Label htmlFor="admin-inventory-q">Search</Label>
              <Input
                id="admin-inventory-q"
                name="q"
                defaultValue={q}
                placeholder="Search name or SKU"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="admin-inventory-active">Product status</Label>
              <select
                id="admin-inventory-active"
                name="active"
                defaultValue={activeParam ?? ''}
                className={adminNativeSelectClass}
              >
                <option value="">All</option>
                <option value="true">Active</option>
                <option value="false">Inactive</option>
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="admin-inventory-stock">Stock status</Label>
              <select
                id="admin-inventory-stock"
                name="stockStatus"
                defaultValue={stockStatus}
                className={adminNativeSelectClass}
              >
                <option value="ALL">All stock</option>
                <option value="IN_STOCK">In stock</option>
                <option value="LOW">Low stock</option>
                <option value="OUT">Out of stock</option>
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="admin-inventory-sort">Sort</Label>
              <select
                id="admin-inventory-sort"
                name="sort"
                defaultValue={sort}
                className={adminNativeSelectClass}
              >
                {ADMIN_INVENTORY_SORT_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            <Button type="submit" variant="outline">
              Apply
            </Button>
          </form>
        </CardContent>
      </Card>

      {listQuery.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <p className="sr-only">Loading inventory…</p>
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-48 w-full rounded-xl" />
        </div>
      ) : listQuery.isError ? (
        <div className={`space-y-3 ${adminAlertErrorClass}`}>
          <p role="alert">
            {listQuery.error instanceof ApiClientError
              ? listQuery.error.userMessage
              : 'Unable to load inventory.'}
          </p>
          <Button type="button" size="sm" onClick={() => void listQuery.refetch()}>
            Retry
          </Button>
        </div>
      ) : data && data.items.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <WarehouseIcon className="size-10 text-muted-foreground opacity-60" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">No products match these inventory filters.</p>
            <Button asChild variant="link">
              <Link to="/admin/products">Browse products</Link>
            </Button>
          </CardContent>
        </Card>
      ) : data ? (
        <>
          <div className={adminTableWrapClass}>
            <table className={`${adminTableClass} min-w-[52rem]`}>
              <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                <tr>
                  <th className={adminThClass}>Product</th>
                  <th className={adminThClass}>SKU</th>
                  <th className={adminThClass}>Active</th>
                  <th className={adminThClass}>On hand</th>
                  <th className={adminThClass}>Reserved</th>
                  <th className={adminThClass}>Available</th>
                  <th className={adminThClass}>Stock</th>
                  <th className={adminThClass}>Actions</th>
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
                              No inventory row yet
                            </p>
                          ) : null}
                        </div>
                      </td>
                      <td className={`${adminTdClass} font-mono text-xs`}>{row.sku}</td>
                      <td className={adminTdClass}>
                        <Badge variant={row.active ? 'success' : 'warning'}>
                          {row.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                      <td className={`${adminTdClass} tabular-nums`}>{row.quantityOnHand}</td>
                      <td className={`${adminTdClass} tabular-nums`}>{row.quantityReserved}</td>
                      <td className={`${adminTdClass} tabular-nums`}>{row.available}</td>
                      <td className={adminTdClass}>
                        <Badge variant={stockBucketBadgeVariant(bucket)}>
                          {stockBucketLabel(bucket)}
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
                            Set on hand
                          </Button>
                          <Button asChild variant="link" size="sm" className="h-auto px-0">
                            <Link to={`/admin/products/${row.productId}/edit`}>Edit product</Link>
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
            itemLabel="products"
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
