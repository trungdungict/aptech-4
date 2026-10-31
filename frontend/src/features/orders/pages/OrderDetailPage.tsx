import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ImageIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { ProductPrice } from '@/features/catalog/components/ProductPrice'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import type { CheckoutResult } from '@/features/checkout/types'
import { ApiClientError } from '@/lib/api/api-error'

import * as ordersApi from '../api/orders-api'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { orderQueryKeys } from '../hooks/order-query-keys'

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

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

export function OrderDetailPage() {
  const { orderId = '' } = useParams<{ orderId: string }>()
  const idValid = UUID_RE.test(orderId)
  const { user } = useAuth()
  const userId = user?.id ?? ''
  const location = useLocation()
  const queryClient = useQueryClient()
  const [confirmCancel, setConfirmCancel] = useState(false)

  const checkoutFlash =
    location.state &&
    typeof location.state === 'object' &&
    'checkoutResult' in location.state
      ? (location.state as { checkoutResult: CheckoutResult }).checkoutResult
      : null

  const orderQuery = useQuery({
    queryKey: orderQueryKeys.detail(userId, orderId),
    queryFn: ({ signal }) => ordersApi.getOrder(orderId, signal),
    enabled: idValid && Boolean(userId),
    retry: false,
  })

  const cancelMutation = useMutation({
    mutationFn: () => ordersApi.cancelOrder(orderId),
    onSuccess: async (order) => {
      setConfirmCancel(false)
      queryClient.setQueryData(orderQueryKeys.detail(userId, orderId), order)
      await queryClient.invalidateQueries({ queryKey: orderQueryKeys.lists(userId) })
      await queryClient.invalidateQueries({ queryKey: ['notifications', userId] })
    },
  })

  const order = orderQuery.data
  const canCancel = order?.status === 'PENDING_PAYMENT'

  return (
    <section className="space-y-6">
      <nav aria-label="Breadcrumb" className="text-sm text-muted-foreground">
        <ol className="flex flex-wrap items-center gap-1.5">
          <li>
            <Link to="/orders" className="font-medium text-primary hover:underline">
              Orders
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li className="font-medium text-foreground">Detail</li>
        </ol>
      </nav>

      {checkoutFlash ? (
        <div
          className={`rounded-xl border px-4 py-3 text-sm ${
            checkoutFlash.orderStatus === 'PAID'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-950'
              : 'border-amber-200 bg-amber-50 text-amber-950'
          }`}
          role="status"
        >
          <p>
            Checkout recorded order{' '}
            <span className="font-mono text-xs">{checkoutFlash.orderId}</span> with status{' '}
            <strong>{checkoutFlash.orderStatus}</strong>
            {checkoutFlash.payment
              ? ` (mock payment: ${checkoutFlash.payment.status})`
              : ''}
            .
          </p>
          <p className="mt-1 text-xs opacity-90">
            Payment is demo/mock only. Cart cleared: {checkoutFlash.cartCleared ? 'yes' : 'no'}.
          </p>
          <p className="mt-1 text-xs opacity-90">
            Live status below is loaded from the orders API and remains accurate after refresh.
          </p>
        </div>
      ) : null}

      {!idValid ? (
        <Unavailable title="Invalid order link" message="The order id in the URL is not valid." />
      ) : orderQuery.isLoading ? (
        <OrderDetailSkeleton />
      ) : orderQuery.isError ? (
        orderQuery.error instanceof ApiClientError && orderQuery.error.status === 404 ? (
          <Unavailable
            title="Order unavailable"
            message="This order was not found or you do not have access to it."
          />
        ) : (
          <div className="space-y-3 rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
            <p role="alert">
              {orderQuery.error instanceof ApiClientError
                ? orderQuery.error.userMessage
                : 'Unable to load order.'}
            </p>
            <Button type="button" size="sm" onClick={() => void orderQuery.refetch()}>
              Retry
            </Button>
          </div>
        )
      ) : order ? (
        <article className="space-y-6">
          <header className="space-y-3">
            <h1 className="text-2xl font-semibold tracking-tight text-foreground md:text-3xl">
              Order detail
            </h1>
            <p className="break-all font-mono text-xs text-muted-foreground">{order.id}</p>
            <div className="flex flex-wrap items-center gap-3">
              <OrderStatusBadge status={order.status} />
              <p className="text-sm text-muted-foreground">
                Placed {formatPlacedAt(order.placedAt)}
              </p>
            </div>
          </header>

          <div className="grid gap-6 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)] lg:items-start">
            <div className="space-y-4">
              <Card className="shadow-sm">
                <CardHeader>
                  <CardTitle>
                    <h2 className="text-base font-semibold">Items (price snapshots)</h2>
                  </CardTitle>
                  <CardDescription>
                    Names and prices captured at checkout — not live catalog values.
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <ul className="divide-y divide-border">
                    {order.items.map((item) => (
                      <li
                        key={`${item.productId}-${item.productSku}`}
                        className="flex gap-3 py-3 first:pt-0 last:pb-0"
                      >
                        <div
                          className="flex size-14 shrink-0 items-center justify-center rounded-lg border border-border bg-muted text-muted-foreground"
                          aria-hidden="true"
                        >
                          <ImageIcon className="size-5 opacity-60" />
                        </div>
                        <div className="min-w-0 flex-1 space-y-1">
                          <p className="text-sm font-medium text-foreground">
                            {item.productName}{' '}
                            <span className="font-normal text-muted-foreground">
                              ({item.productSku})
                            </span>
                          </p>
                          <p className="text-sm text-muted-foreground">
                            {formatProductPrice(item.unitPrice, order.currency)} each ×{' '}
                            {item.quantity}
                          </p>
                        </div>
                        <ProductPrice
                          unitPrice={item.lineTotal}
                          currency={order.currency}
                          className="shrink-0 text-sm font-medium text-foreground"
                        />
                      </li>
                    ))}
                  </ul>
                </CardContent>
              </Card>

              <Card className="shadow-sm">
                <CardHeader>
                  <CardTitle>
                    <h2 className="text-base font-semibold">Shipping</h2>
                  </CardTitle>
                </CardHeader>
                <CardContent className="text-sm text-muted-foreground">
                  <p>
                    {order.shipping.recipientName}
                    <br />
                    {order.shipping.line1}
                    {order.shipping.line2 ? (
                      <>
                        <br />
                        {order.shipping.line2}
                      </>
                    ) : null}
                    <br />
                    {order.shipping.city}
                    {order.shipping.stateRegion ? `, ${order.shipping.stateRegion}` : ''}{' '}
                    {order.shipping.postalCode ?? ''} {order.shipping.countryCode}
                    {order.shipping.phone ? (
                      <>
                        <br />
                        {order.shipping.phone}
                      </>
                    ) : null}
                  </p>
                </CardContent>
              </Card>

              {order.payment ? (
                <Card className="shadow-sm">
                  <CardHeader>
                    <CardTitle>
                      <h2 className="text-base font-semibold">Payment (mock)</h2>
                    </CardTitle>
                    <CardDescription>As returned by the orders API — demo payment only.</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-1 text-sm text-muted-foreground">
                    <p>
                      Provider {order.payment.provider} · Status {order.payment.status}
                      {order.payment.providerReference
                        ? ` · Ref ${order.payment.providerReference}`
                        : ''}
                    </p>
                    <p>
                      Amount {formatProductPrice(order.payment.amount, order.payment.currency)}
                    </p>
                    {order.payment.failureReason ? (
                      <p>Failure: {order.payment.failureReason}</p>
                    ) : null}
                  </CardContent>
                </Card>
              ) : null}
            </div>

            <div className="space-y-4">
              <Card className="sticky top-20 shadow-sm">
                <CardHeader>
                  <CardTitle>
                    <h2 className="text-base font-semibold">Order summary</h2>
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                  <div className="flex justify-between gap-3">
                    <span className="text-muted-foreground">Subtotal</span>
                    <ProductPrice
                      unitPrice={order.subtotalAmount}
                      currency={order.currency}
                      className="text-sm font-medium text-foreground"
                    />
                  </div>
                  <div className="flex justify-between gap-3">
                    <span className="text-muted-foreground">Shipping</span>
                    <ProductPrice
                      unitPrice={order.shippingAmount}
                      currency={order.currency}
                      className="text-sm font-medium text-foreground"
                    />
                  </div>
                  <Separator />
                  <div className="flex justify-between gap-3 font-semibold">
                    <span>Total</span>
                    <ProductPrice
                      unitPrice={order.totalAmount}
                      currency={order.currency}
                      className="text-base font-semibold text-foreground"
                    />
                  </div>
                </CardContent>
              </Card>

              {canCancel ? (
                <Card className="shadow-sm">
                  <CardHeader>
                    <CardTitle>
                      <h2 className="text-base font-semibold">Cancel order</h2>
                    </CardTitle>
                    <CardDescription>
                      Customer cancel is only allowed while status is PENDING_PAYMENT.
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    {cancelMutation.isError ? (
                      <p className="text-sm text-destructive" role="alert">
                        {cancelMutation.error instanceof ApiClientError
                          ? cancelMutation.error.userMessage
                          : 'Cancel failed.'}
                      </p>
                    ) : null}

                    {!confirmCancel ? (
                      <Button
                        type="button"
                        variant="outline"
                        disabled={cancelMutation.isPending}
                        onClick={() => setConfirmCancel(true)}
                      >
                        Cancel order
                      </Button>
                    ) : (
                      <div
                        className="space-y-3 rounded-xl border border-border bg-muted/40 p-3"
                        role="alertdialog"
                        aria-labelledby="cancel-order-title"
                        aria-describedby="cancel-order-desc"
                      >
                        <p id="cancel-order-title" className="text-sm font-medium text-foreground">
                          Confirm cancellation?
                        </p>
                        <p id="cancel-order-desc" className="text-xs text-muted-foreground">
                          This cannot be undone from the storefront. The server will only cancel if
                          the order is still PENDING_PAYMENT.
                        </p>
                        <div className="flex flex-wrap gap-2">
                          <Button
                            type="button"
                            variant="destructive"
                            disabled={cancelMutation.isPending}
                            onClick={() => cancelMutation.mutate()}
                          >
                            {cancelMutation.isPending ? 'Cancelling…' : 'Confirm cancel'}
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            disabled={cancelMutation.isPending}
                            onClick={() => setConfirmCancel(false)}
                          >
                            Keep order
                          </Button>
                        </div>
                      </div>
                    )}
                  </CardContent>
                </Card>
              ) : null}

              <Button asChild variant="outline" className="w-full sm:w-auto">
                <Link to="/orders">Back to orders</Link>
              </Button>
            </div>
          </div>
        </article>
      ) : null}
    </section>
  )
}

function OrderDetailSkeleton() {
  return (
    <div className="space-y-4" role="status" aria-live="polite">
      <p className="sr-only">Loading order…</p>
      <Skeleton className="h-10 w-48" />
      <Skeleton className="h-4 w-72" />
      <div className="grid gap-4 lg:grid-cols-2">
        <Skeleton className="h-56 w-full rounded-xl" />
        <Skeleton className="h-40 w-full rounded-xl" />
      </div>
    </div>
  )
}

function Unavailable({ title, message }: { title: string; message: string }) {
  return (
    <Card className="border-dashed shadow-none">
      <CardContent className="flex flex-col items-center gap-3 px-6 py-10 text-center">
        <h1 className="text-xl font-semibold text-foreground">{title}</h1>
        <p className="text-sm text-muted-foreground">{message}</p>
        <Button asChild variant="link">
          <Link to="/orders">Back to orders</Link>
        </Button>
      </CardContent>
    </Card>
  )
}
