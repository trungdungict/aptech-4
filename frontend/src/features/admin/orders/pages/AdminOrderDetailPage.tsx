import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { ApiClientError } from '@/lib/api/api-error'

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
  }
  catch {
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
      <p className="text-sm text-muted-foreground" role="status">
        Loading order…
      </p>
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
  }

  const order = orderQuery.data
  if (!order) {
    return null
  }

  const transitions = allowedAdminTransitions(order.status)

  return (
    <section className="space-y-6">
      <p className="text-sm text-muted-foreground">
        <Link to="/admin/orders" className="text-primary underline-offset-4 hover:underline">
          Orders
        </Link>
        <span aria-hidden="true"> / </span>
        Detail
      </p>

      <header className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">Order detail</h1>
        <p className="font-mono text-xs text-muted-foreground">{order.id}</p>
        <p className="text-sm text-muted-foreground">
          Placed {formatPlacedAt(order.placedAt)} · Updated {formatPlacedAt(order.updatedAt)} ·
          Status <span className="font-medium text-foreground">{order.status}</span>
        </p>
        <p className="text-sm text-muted-foreground">
          Customer userId:{' '}
          <span className="font-mono text-xs text-foreground">{order.userId}</span>
        </p>
      </header>

      <div className="rounded-2xl border border-border bg-card p-4">
        <h2 className="text-sm font-semibold text-foreground">Items (price snapshots)</h2>
        <ul className="mt-3 divide-y divide-border">
          {order.items.map((item) => (
            <li
              key={`${item.productId}-${item.productSku}`}
              className="flex justify-between gap-4 py-2 text-sm"
            >
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
        <h2 className="font-semibold text-foreground">Shipping snapshot</h2>
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
          <p className="mt-2 text-xs text-muted-foreground">
            Admins cannot set PAID or FAILED via this UI — those statuses are checkout-owned.
          </p>
        </div>
      ) : null}

      <div className="space-y-3 rounded-2xl border border-border bg-card p-4">
        <h2 className="text-sm font-semibold text-foreground">Fulfillment actions</h2>
        <p className="text-xs text-muted-foreground">
          Only whitelist transitions from the current status are shown. Invalid transitions are
          rejected by the server (409).
        </p>
        {transitions.length === 0 ? (
          <p className="text-sm text-muted-foreground">No admin transitions available from this status.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {transitions.map((nextStatus) => (
              <button
                key={nextStatus}
                type="button"
                disabled={statusMutation.isPending}
                className="rounded-lg border border-border px-3 py-2 text-sm font-medium hover:bg-muted disabled:opacity-60"
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
              </button>
            ))}
          </div>
        )}
        {actionError ? (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
            {actionError}
          </p>
        ) : null}
        {successMessage ? (
          <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-900" role="status">
            {successMessage}
          </p>
        ) : null}
      </div>
    </section>
  )
}

function Unavailable({ title, message }: { title: string; message: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-border bg-card px-6 py-10 text-center">
      <h1 className="text-xl font-semibold text-foreground">{title}</h1>
      <p className="mt-2 text-sm text-muted-foreground">{message}</p>
      <Link
        to="/admin/orders"
        className="mt-4 inline-flex text-sm font-medium text-primary underline-offset-4 hover:underline"
      >
        Back to orders
      </Link>
    </div>
  )
}
