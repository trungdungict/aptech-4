import { useQuery } from '@tanstack/react-query'
import { PackageIcon } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { ProductPrice } from '@/features/catalog/components/ProductPrice'
import { ApiClientError } from '@/lib/api/api-error'

import * as ordersApi from '../api/orders-api'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { orderQueryKeys } from '../hooks/order-query-keys'
import { ORDER_DEFAULT_PAGE_SIZE, ORDER_DEFAULT_SORT } from '../types'

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
        <OrderHistorySkeleton />
      ) : ordersQuery.isError ? (
        <div className="space-y-3 rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <p role="alert">
            {ordersQuery.error instanceof ApiClientError
              ? ordersQuery.error.status === 403
                ? 'Orders are available for customer accounts only.'
                : ordersQuery.error.userMessage
              : 'Unable to load orders.'}
          </p>
          <Button type="button" size="sm" onClick={() => void ordersQuery.refetch()}>
            Retry
          </Button>
        </div>
      ) : data && data.items.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <PackageIcon className="size-10 text-muted-foreground opacity-60" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">No orders yet.</p>
            <Button asChild>
              <Link to="/products">Browse catalog</Link>
            </Button>
          </CardContent>
        </Card>
      ) : data ? (
        <>
          <p className="text-sm text-muted-foreground">
            Showing page {data.page + 1} of {Math.max(data.totalPages, 1)} ({data.totalElements}{' '}
            total)
          </p>
          <ul className="space-y-3">
            {data.items.map((order) => (
              <li key={order.id}>
                <Link
                  to={`/orders/${order.id}`}
                  className="block rounded-xl border border-border bg-card p-4 shadow-sm transition hover:border-primary/40 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                >
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0 space-y-2">
                      <p className="font-medium text-foreground">
                        Order <span className="font-mono text-sm">{order.id}</span>
                      </p>
                      <p className="text-sm text-muted-foreground">
                        {formatPlacedAt(order.placedAt)}
                      </p>
                      <OrderStatusBadge status={order.status} />
                    </div>
                    <ProductPrice
                      unitPrice={order.totalAmount}
                      currency={order.currency}
                      className="text-base font-semibold text-foreground"
                    />
                  </div>
                </Link>
              </li>
            ))}
          </ul>
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

function OrderHistorySkeleton() {
  return (
    <div className="space-y-3" role="status" aria-live="polite">
      <p className="sr-only">Loading orders…</p>
      {Array.from({ length: 3 }, (_, i) => (
        <Skeleton key={i} className="h-24 w-full rounded-xl" />
      ))}
    </div>
  )
}
