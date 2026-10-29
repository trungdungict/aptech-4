import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/hooks/useAuth'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { ApiClientError } from '@/lib/api/api-error'
import type { CheckoutResult } from '@/features/checkout/types'

import * as ordersApi from '../api/orders-api'
import { orderQueryKeys } from '../hooks/order-query-keys'

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

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

export function OrderDetailPage() {
  const { orderId = '' } = useParams<{ orderId: string }>()
  const idValid = UUID_RE.test(orderId)
  const { user } = useAuth()
  const userId = user?.id ?? ''
  const location = useLocation()
  const queryClient = useQueryClient()

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
      queryClient.setQueryData(orderQueryKeys.detail(userId, orderId), order)
      await queryClient.invalidateQueries({ queryKey: orderQueryKeys.lists(userId) })
      await queryClient.invalidateQueries({ queryKey: ['notifications', userId] })
    },
  })

  const order = orderQuery.data

  return (
    <section className="space-y-6">
      <p className="text-sm font-medium text-primary">
        <Link to="/orders" className="hover:underline">
          Orders
        </Link>
        <span aria-hidden="true"> / </span>
        Detail
      </p>

      {checkoutFlash ? (
        <div
          className={`rounded-lg border px-4 py-3 text-sm ${
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
        </div>
      ) : null}

      {!idValid ? (
        <Unavailable title="Invalid order link" message="The order id in the URL is not valid." />
      ) : orderQuery.isLoading ? (
        <p className="text-sm text-muted-foreground" role="status">
          Loading order…
        </p>
      ) : orderQuery.isError ? (
        orderQuery.error instanceof ApiClientError && orderQuery.error.status === 404 ? (
          <Unavailable
            title="Order unavailable"
            message="This order was not found or you do not have access to it."
          />
        ) : (
          <div className="space-y-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            <p role="alert">
              {orderQuery.error instanceof ApiClientError
                ? orderQuery.error.userMessage
                : 'Unable to load order.'}
            </p>
            <button
              type="button"
              className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground"
              onClick={() => void orderQuery.refetch()}
            >
              Retry
            </button>
          </div>
        )
      ) : order ? (
        <article className="space-y-6">
          <header className="space-y-1">
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">Order detail</h1>
            <p className="font-mono text-xs text-muted-foreground">{order.id}</p>
            <p className="text-sm text-muted-foreground">
              Placed {formatPlacedAt(order.placedAt)} · Status {order.status}
            </p>
          </header>

          <div className="rounded-2xl border border-border bg-card p-4">
            <h2 className="text-sm font-semibold text-foreground">Items (price snapshots)</h2>
            <ul className="mt-3 divide-y divide-border">
              {order.items.map((item) => (
                <li key={`${item.productId}-${item.productSku}`} className="flex justify-between gap-4 py-2 text-sm">
                  <span>
                    {item.productName} ({item.productSku}) × {item.quantity}
                    <br />
                    <span className="text-muted-foreground">
                      {formatProductPrice(item.unitPrice, order.currency)} each
                    </span>
                  </span>
                  <span className="font-medium">
                    {formatProductPrice(item.lineTotal, order.currency)}
                  </span>
                </li>
              ))}
            </ul>
            <dl className="mt-4 space-y-1 text-sm">
              <div className="flex justify-between">
                <dt className="text-muted-foreground">Subtotal</dt>
                <dd>{formatProductPrice(order.subtotalAmount, order.currency)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-muted-foreground">Shipping</dt>
                <dd>{formatProductPrice(order.shippingAmount, order.currency)}</dd>
              </div>
              <div className="flex justify-between font-semibold">
                <dt>Total</dt>
                <dd>{formatProductPrice(order.totalAmount, order.currency)}</dd>
              </div>
            </dl>
          </div>

          <div className="rounded-2xl border border-border bg-card p-4 text-sm">
            <h2 className="font-semibold text-foreground">Shipping</h2>
            <p className="mt-2 text-muted-foreground">
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
          </div>

          {order.payment ? (
            <div className="rounded-2xl border border-border bg-card p-4 text-sm">
              <h2 className="font-semibold text-foreground">Payment (mock)</h2>
              <p className="mt-2 text-muted-foreground">
                Provider {order.payment.provider} · Status {order.payment.status}
                {order.payment.providerReference
                  ? ` · Ref ${order.payment.providerReference}`
                  : ''}
                {order.payment.failureReason ? (
                  <>
                    <br />
                    Failure: {order.payment.failureReason}
                  </>
                ) : null}
              </p>
            </div>
          ) : null}

          {order.status === 'PENDING_PAYMENT' ? (
            <div className="space-y-2">
              {cancelMutation.isError ? (
                <p className="text-sm text-red-700" role="alert">
                  {cancelMutation.error instanceof ApiClientError
                    ? cancelMutation.error.userMessage
                    : 'Cancel failed.'}
                </p>
              ) : null}
              <button
                type="button"
                disabled={cancelMutation.isPending}
                className="rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-muted disabled:opacity-60"
                onClick={() => cancelMutation.mutate()}
              >
                {cancelMutation.isPending ? 'Cancelling…' : 'Cancel order'}
              </button>
              <p className="text-xs text-muted-foreground">
                Customer cancel is only allowed while status is PENDING_PAYMENT.
              </p>
            </div>
          ) : null}
        </article>
      ) : null}
    </section>
  )
}

function Unavailable({ title, message }: { title: string; message: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-border bg-card px-6 py-10 text-center">
      <h1 className="text-xl font-semibold text-foreground">{title}</h1>
      <p className="mt-2 text-sm text-muted-foreground">{message}</p>
      <Link
        to="/orders"
        className="mt-4 inline-flex text-sm font-medium text-primary underline-offset-4 hover:underline"
      >
        Back to orders
      </Link>
    </div>
  )
}
