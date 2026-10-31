import { useQuery } from '@tanstack/react-query'
import { ClipboardListIcon } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { OrderStatusBadge } from '@/features/orders/components/OrderStatusBadge'
import { ORDER_DEFAULT_PAGE_SIZE, ORDER_DEFAULT_SORT } from '@/features/orders/types'
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

import * as ordersApi from '../api/admin-orders-api'
import { adminOrderQueryKeys } from '../hooks/admin-order-query-keys'
import { ORDER_STATUS_VALUES } from '../lib/admin-order-transitions'

function formatPlacedAt(iso: string): string {
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(iso))
  } catch {
    return iso
  }
}

const SORT_OPTIONS = [
  { value: 'placedAt,desc', label: 'Placed (newest)' },
  { value: 'placedAt,asc', label: 'Placed (oldest)' },
  { value: 'totalAmount,desc', label: 'Total (high → low)' },
  { value: 'totalAmount,asc', label: 'Total (low → high)' },
  { value: 'status,asc', label: 'Status (A–Z)' },
  { value: 'status,desc', label: 'Status (Z–A)' },
]

export function AdminOrderListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const pageRaw = Number(searchParams.get('page') ?? '0')
  const page = Number.isInteger(pageRaw) && pageRaw >= 0 ? pageRaw : 0
  const status = searchParams.get('status') ?? ''
  const sortParam = searchParams.get('sort') ?? ORDER_DEFAULT_SORT
  const sort = SORT_OPTIONS.some((option) => option.value === sortParam)
    ? sortParam
    : ORDER_DEFAULT_SORT

  const listParams = {
    status: status || undefined,
    page,
    size: ORDER_DEFAULT_PAGE_SIZE,
    sort,
  }

  const ordersQuery = useQuery({
    queryKey: adminOrderQueryKeys.list(listParams),
    queryFn: ({ signal }) => ordersApi.listAdminOrders(listParams, signal),
    retry: false,
  })

  const data = ordersQuery.data

  return (
    <section className={adminPageSectionClass}>
      <div className="space-y-1">
        <h1 className={adminPageTitleClass}>
          Orders
        </h1>
        <p className={adminPageLeadClass}>
          All customer orders (admin). Totals and line items are checkout snapshots. Customer email
          is not returned by this API — only userId.
        </p>
      </div>

      <Card className="shadow-sm">
        <CardContent className="pt-6">
          <form
            className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end"
            onSubmit={(event) => {
              event.preventDefault()
              const form = new FormData(event.currentTarget)
              const next = new URLSearchParams()
              const nextStatus = String(form.get('status') ?? '')
              const nextSort = String(form.get('sort') ?? ORDER_DEFAULT_SORT)
              if (nextStatus) next.set('status', nextStatus)
              next.set('sort', nextSort)
              next.set('page', '0')
              setSearchParams(next)
            }}
          >
            <div className="space-y-1.5">
              <Label htmlFor="admin-order-status">Status filter</Label>
              <select
                id="admin-order-status"
                name="status"
                defaultValue={status}
                className={adminNativeSelectClass}
              >
                <option value="">All statuses</option>
                {ORDER_STATUS_VALUES.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="admin-order-sort">Sort</Label>
              <select
                id="admin-order-sort"
                name="sort"
                defaultValue={sort}
                className={adminNativeSelectClass}
              >
                {SORT_OPTIONS.map((option) => (
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

      {ordersQuery.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <p className="sr-only">Loading orders…</p>
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-48 w-full rounded-xl" />
        </div>
      ) : ordersQuery.isError ? (
        <div className={`space-y-3 ${adminAlertErrorClass}`}>
          <p role="alert">
            {ordersQuery.error instanceof ApiClientError
              ? ordersQuery.error.userMessage
              : 'Unable to load orders.'}
          </p>
          <Button type="button" size="sm" onClick={() => void ordersQuery.refetch()}>
            Retry
          </Button>
        </div>
      ) : data && data.items.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <ClipboardListIcon
              className="size-10 text-muted-foreground opacity-60"
              aria-hidden="true"
            />
            <p className="text-sm text-muted-foreground">No orders match these filters.</p>
          </CardContent>
        </Card>
      ) : data ? (
        <>
          <p className="text-sm text-muted-foreground">
            Showing page {data.page + 1} of {Math.max(data.totalPages, 1)} ({data.totalElements}{' '}
            total)
          </p>
          <div className={adminTableWrapClass}>
            <table className={`${adminTableClass} min-w-[44rem]`}>
              <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                <tr>
                  <th className={adminThClass}>Order</th>
                  <th className={adminThClass}>Customer userId</th>
                  <th className={adminThClass}>Placed</th>
                  <th className={adminThClass}>Status</th>
                  <th className={adminThClass}>Total</th>
                  <th className={adminThClass}>Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.items.map((order) => (
                  <tr key={order.id} className="hover:bg-muted/30">
                    <td className={adminTdClass}>
                      <Link
                        to={`/admin/orders/${order.id}`}
                        className="font-mono text-xs font-medium text-primary underline-offset-4 hover:underline"
                      >
                        {order.id}
                      </Link>
                    </td>
                    <td className={`${adminTdClass} font-mono text-xs text-muted-foreground`}>
                      {order.userId}
                    </td>
                    <td className={`${adminTdClass} text-muted-foreground`}>
                      {formatPlacedAt(order.placedAt)}
                    </td>
                    <td className={adminTdClass}>
                      <OrderStatusBadge status={order.status} />
                    </td>
                    <td className={`${adminTdClass} font-semibold text-foreground`}>
                      {formatProductPrice(order.totalAmount, order.currency)}
                    </td>
                    <td className={adminTdClass}>
                      <Button asChild variant="link" size="sm" className="h-auto px-0">
                        <Link to={`/admin/orders/${order.id}`}>View</Link>
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={data.first}
              onClick={() => {
                const next = new URLSearchParams(searchParams)
                next.set('page', String(Math.max(0, page - 1)))
                setSearchParams(next)
              }}
            >
              Previous
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={data.last}
              onClick={() => {
                const next = new URLSearchParams(searchParams)
                next.set('page', String(page + 1))
                setSearchParams(next)
              }}
            >
              Next
            </Button>
          </div>
        </>
      ) : null}
    </section>
  )
}
