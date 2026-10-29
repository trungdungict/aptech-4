import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'

import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { ORDER_DEFAULT_PAGE_SIZE, ORDER_DEFAULT_SORT } from '@/features/orders/types'
import { ApiClientError } from '@/lib/api/api-error'

import * as ordersApi from '../api/admin-orders-api'
import { adminOrderQueryKeys } from '../hooks/admin-order-query-keys'
import { ORDER_STATUS_VALUES } from '../lib/admin-order-transitions'

function formatPlacedAt(iso: string): string {
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(iso))
  }
  catch {
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
    <section className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">Orders</h1>
        <p className="text-sm text-muted-foreground">
          All customer orders (admin). Totals and line items are checkout snapshots. Customer email
          is not returned by this API — only userId.
        </p>
      </div>

      <form
        className="flex flex-wrap gap-2"
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
        <label className="sr-only" htmlFor="admin-order-status">
          Status filter
        </label>
        <select
          id="admin-order-status"
          name="status"
          defaultValue={status}
          className="rounded-lg border border-border bg-background px-3 py-2 text-sm"
        >
          <option value="">All statuses</option>
          {ORDER_STATUS_VALUES.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
        <label className="sr-only" htmlFor="admin-order-sort">
          Sort
        </label>
        <select
          id="admin-order-sort"
          name="sort"
          defaultValue={sort}
          className="rounded-lg border border-border bg-background px-3 py-2 text-sm"
        >
          {SORT_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <button
          type="submit"
          className="rounded-lg border border-border px-3 py-2 text-sm font-medium hover:bg-muted"
        >
          Apply
        </button>
      </form>

      {ordersQuery.isLoading ? (
        <p className="text-sm text-muted-foreground" role="status">
          Loading orders…
        </p>
      ) : ordersQuery.isError ? (
        <div className="space-y-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          <p role="alert">
            {ordersQuery.error instanceof ApiClientError
              ? ordersQuery.error.userMessage
              : 'Unable to load orders.'}
          </p>
          <button
            type="button"
            className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground"
            onClick={() => void ordersQuery.refetch()}
          >
            Retry
          </button>
        </div>
      ) : data && data.items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-card px-6 py-10 text-center">
          <p className="text-sm text-muted-foreground">No orders match these filters.</p>
        </div>
      ) : data ? (
        <>
          <p className="text-sm text-muted-foreground">
            Showing page {data.page + 1} of {Math.max(data.totalPages, 1)} ({data.totalElements}{' '}
            total)
          </p>
          <div className="overflow-x-auto rounded-2xl border border-border bg-card">
            <table className="w-full min-w-[44rem] text-left text-sm">
              <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">Order</th>
                  <th className="px-4 py-3 font-medium">Customer userId</th>
                  <th className="px-4 py-3 font-medium">Placed</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Total</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.items.map((order) => (
                  <tr key={order.id}>
                    <td className="px-4 py-3">
                      <Link
                        to={`/admin/orders/${order.id}`}
                        className="font-mono text-xs font-medium text-primary underline-offset-4 hover:underline"
                      >
                        {order.id}
                      </Link>
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                      {order.userId}
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {formatPlacedAt(order.placedAt)}
                    </td>
                    <td className="px-4 py-3 font-medium text-foreground">{order.status}</td>
                    <td className="px-4 py-3 font-semibold">
                      {formatProductPrice(order.totalAmount, order.currency)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={data.first}
              className="rounded-lg border border-border px-3 py-1.5 text-sm font-medium disabled:opacity-50"
              onClick={() => {
                const next = new URLSearchParams(searchParams)
                next.set('page', String(Math.max(0, page - 1)))
                setSearchParams(next)
              }}
            >
              Previous
            </button>
            <button
              type="button"
              disabled={data.last}
              className="rounded-lg border border-border px-3 py-1.5 text-sm font-medium disabled:opacity-50"
              onClick={() => {
                const next = new URLSearchParams(searchParams)
                next.set('page', String(page + 1))
                setSearchParams(next)
              }}
            >
              Next
            </button>
          </div>
        </>
      ) : null}
    </section>
  )
}
