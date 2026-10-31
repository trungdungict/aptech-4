import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ImageIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { ProductPrice } from '@/features/catalog/components/ProductPrice'
import { OrderStatusBadge } from '@/features/orders/components/OrderStatusBadge'
import { ApiClientError } from '@/lib/api/api-error'

import {
  adminAlertErrorClass,
  adminAlertSuccessClass,
  adminPageSectionClass,
  adminPageTitleClass,
} from '../../lib/admin-ui'

import * as ordersApi from '../api/admin-orders-api'
import { adminOrderQueryKeys } from '../hooks/admin-order-query-keys'
import { allowedAdminTransitions } from '../lib/admin-order-transitions'

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

export function AdminOrderDetailPage() {
  const { orderId = '' } = useParams<{ orderId: string }>()
  const idValid = UUID_RE.test(orderId)
  const queryClient = useQueryClient()
  const [actionError, setActionError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const orderQuery = useQuery({
    queryKey: adminOrderQueryKeys.detail(orderId),
    queryFn: ({ signal }) => ordersApi.getAdminOrder(orderId, signal),
    enabled: idValid,
    retry: false,
  })

  const statusMutation = useMutation({
    mutationFn: (status: string) => ordersApi.updateAdminOrderStatus(orderId, { status }),
    onSuccess: async (order) => {
      setActionError(null)
      setSuccessMessage(`Status updated to ${order.status}.`)
      queryClient.setQueryData(adminOrderQueryKeys.detail(orderId), order)
      await queryClient.invalidateQueries({ queryKey: adminOrderQueryKeys.lists() })
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
    onError: (error) => {
      setSuccessMessage(null)
      setActionError(
        error instanceof ApiClientError ? error.userMessage : 'Status update failed.',
      )
    },
  })

  if (!idValid) {
    return (
      <Unavailable title="Invalid order link" message="The order id in the URL is not valid." />
    )
  }

  if (orderQuery.isLoading) {
    return (
      <div className="space-y-4" role="status" aria-live="polite">
        <p className="sr-only">Loading order…</p>
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-72" />
        <div className="grid gap-4 lg:grid-cols-2">
          <Skeleton className="h-56 w-full rounded-xl" />
          <Skeleton className="h-40 w-full rounded-xl" />
        </div>
      </div>
    )
  }

  if (orderQuery.isError) {
    if (orderQuery.error instanceof ApiClientError && orderQuery.error.status === 404) {
      return (
        <Unavailable
          title="Order unavailable"
          message="This order was not found or is not accessible."
        />
      )
    }
    return (
      <div className={`space-y-3 ${adminAlertErrorClass}`}>
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
  }

  const order = orderQuery.data
  if (!order) {
    return null
  }

  const transitions = allowedAdminTransitions(order.status)

  return (
    <section className={adminPageSectionClass}>
      <nav aria-label="Breadcrumb" className="text-sm text-muted-foreground">
        <ol className="flex flex-wrap items-center gap-1.5">
          <li>
            <Link to="/admin/orders" className="font-medium text-primary hover:underline">
              Orders
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li className="font-medium text-foreground">Detail</li>
        </ol>
      </nav>

      <header className="space-y-3">
        <h1 className={adminPageTitleClass}>
          Order detail
        </h1>
        <p className="break-all font-mono text-xs text-muted-foreground">{order.id}</p>
        <div className="flex flex-wrap items-center gap-3">
          <OrderStatusBadge status={order.status} />
          <p className="text-sm text-muted-foreground">
            Placed {formatPlacedAt(order.placedAt)} · Updated {formatPlacedAt(order.updatedAt)}
          </p>
        </div>
        <p className="text-sm text-muted-foreground">
          Customer userId:{' '}
          <span className="font-mono text-xs text-foreground">{order.userId}</span>
        </p>
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
                        {formatProductPrice(item.unitPrice, order.currency)} each × {item.quantity}
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
                <h2 className="text-base font-semibold">Shipping snapshot</h2>
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
                <CardDescription>As returned by the admin orders API.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-2 text-sm text-muted-foreground">
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
                <p className="text-xs">
                  Admins cannot set PAID or FAILED via this UI — those statuses are checkout-owned.
                </p>
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

          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle>
                <h2 className="text-base font-semibold">Fulfillment actions</h2>
              </CardTitle>
              <CardDescription>
                Only whitelist transitions from the current status are shown. Invalid transitions
                are rejected by the server (409).
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {transitions.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  No admin transitions available from this status.
                </p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {transitions.map((nextStatus) => (
                    <Button
                      key={nextStatus}
                      type="button"
                      variant={nextStatus === 'CANCELLED' ? 'outline' : 'default'}
                      disabled={statusMutation.isPending}
                      onClick={() => {
                        const ok = window.confirm(
                          `Change order status from ${order.status} to ${nextStatus}?`,
                        )
                        if (!ok) return
                        setActionError(null)
                        setSuccessMessage(null)
                        statusMutation.mutate(nextStatus)
                      }}
                    >
                      {statusMutation.isPending && statusMutation.variables === nextStatus
                        ? 'Updating…'
                        : `Set ${nextStatus}`}
                    </Button>
                  ))}
                </div>
              )}
              {actionError ? (
                <p
                  className={adminAlertErrorClass}
                  role="alert"
                >
                  {actionError}
                </p>
              ) : null}
              {successMessage ? (
                <p
                  className={adminAlertSuccessClass}
                  role="status"
                >
                  {successMessage}
                </p>
              ) : null}
            </CardContent>
          </Card>

          <Button asChild variant="outline" className="w-full sm:w-auto">
            <Link to="/admin/orders">Back to orders</Link>
          </Button>
        </div>
      </div>
    </section>
  )
}

function Unavailable({ title, message }: { title: string; message: string }) {
  return (
    <Card className="border-dashed shadow-none">
      <CardContent className="flex flex-col items-center gap-3 px-6 py-10 text-center">
        <h1 className="text-xl font-semibold text-foreground">{title}</h1>
        <p className="text-sm text-muted-foreground">{message}</p>
        <Button asChild variant="link">
          <Link to="/admin/orders">Back to orders</Link>
        </Button>
      </CardContent>
    </Card>
  )
}
