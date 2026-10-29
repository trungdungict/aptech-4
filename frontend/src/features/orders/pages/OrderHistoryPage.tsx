import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/hooks/useAuth'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { ApiClientError } from '@/lib/api/api-error'

import * as ordersApi from '../api/orders-api'
import { orderQueryKeys } from '../hooks/order-query-keys'
import { ORDER_DEFAULT_PAGE_SIZE, ORDER_DEFAULT_SORT } from '../types'

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

export function OrderHistoryPage() {
  const { user } = useAuth()
  const userId = user?.id ?? ''
  const [searchParams, setSearchParams] = useSearchParams()
  const pageRaw = Number(searchParams.get('page') ?? '0')
  const page = Number.isInteger(pageRaw) && pageRaw >= 0 ? pageRaw : 0

  const listParams = {
    page,
    size: ORDER_DEFAULT_PAGE_SIZE,
    sort: ORDER_DEFAULT_SORT,
  }

  const ordersQuery = useQuery({
    queryKey: orderQueryKeys.list(userId, listParams),
    queryFn: ({ signal }) => ordersApi.listOrders(listParams, signal),
    enabled: Boolean(userId),
    retry: false,
  })

  const data = ordersQuery.data

  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight text-foreground">Orders</h1>
        <p className="text-sm text-muted-foreground">
          Your order history from the API. Prices are snapshots from checkout, not live catalog
          prices.
        </p>
      </div>

      {ordersQuery.isLoading ? (
        <p className="text-sm text-muted-foreground" role="status">
          Loading orders…
        </p>
      ) : ordersQuery.isError ? (
        <div className="space-y-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          <p role="alert">
            {ordersQuery.error instanceof ApiClientError
              ? ordersQuery.error.status === 403
                ? 'Orders are available for customer accounts only.'
                : ordersQuery.error.userMessage
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
          <p className="text-sm text-muted-foreground">No orders yet.</p>
          <Link
            to="/products"
            className="mt-4 inline-flex rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
          >
            Browse catalog
          </Link>
        </div>
      ) : data ? (
        <>
          <p className="text-sm text-muted-foreground">
            Showing page {data.page + 1} of {Math.max(data.totalPages, 1)} ({data.totalElements}{' '}
            total)
          </p>
          <ul className="divide-y divide-border rounded-2xl border border-border bg-card">
            {data.items.map((order) => (
              <li key={order.id}>
                <Link
                  to={`/orders/${order.id}`}
                  className="flex flex-col gap-1 px-4 py-4 hover:bg-muted sm:flex-row sm:items-center sm:justify-between"
                >
                  <div>
                    <p className="font-medium text-foreground">
                      Order <span className="font-mono text-sm">{order.id}</span>
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {formatPlacedAt(order.placedAt)} · {order.status}
                    </p>
                  </div>
                  <p className="text-sm font-semibold text-foreground">
                    {formatProductPrice(order.totalAmount, order.currency)}
                  </p>
                </Link>
              </li>
            ))}
          </ul>
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
