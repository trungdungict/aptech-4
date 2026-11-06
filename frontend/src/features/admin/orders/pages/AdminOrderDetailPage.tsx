import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ImageIcon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ProductPrice } from '@/features/catalog/components/ProductPrice'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { OrderPriceSummary } from '@/features/orders/components/OrderPriceSummary'
import { OrderStatusBadge } from '@/features/orders/components/OrderStatusBadge'
import { ApiClientError } from '@/lib/api/api-error'
import { formatDateTime } from '@/lib/i18n'

import {
  adminAlertErrorClass,
  adminAlertSuccessClass,
  adminPageSectionClass,
  adminPageTitleClass,
} from '../../lib/admin-ui'

import * as ordersApi from '../api/admin-orders-api'
import { adminOrderQueryKeys } from '../hooks/admin-order-query-keys'
import { allowedAdminTransitions } from '../lib/admin-order-transitions'
import { useOrderStatusLabel } from '../lib/use-order-status-label'

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function formatPlacedAt(iso: string): string {
  return formatDateTime(iso, { dateStyle: 'medium', timeStyle: 'short' })
}

export function AdminOrderDetailPage() {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const statusLabel = useOrderStatusLabel()
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
      setSuccessMessage(t('orders.detail.statusUpdated', { status: statusLabel(order.status) }))
      queryClient.setQueryData(adminOrderQueryKeys.detail(orderId), order)
      await queryClient.invalidateQueries({ queryKey: adminOrderQueryKeys.lists() })
      await queryClient.invalidateQueries({ queryKey: ['notifications'] })
    },
    onError: (error) => {
      setSuccessMessage(null)
      setActionError(
        error instanceof ApiClientError ? error.userMessage : t('orders.detail.statusFailed'),
      )
    },
  })

  if (!idValid) {
    return (
      <Unavailable
        title={t('orders.detail.invalidLinkTitle')}
        message={t('orders.detail.invalidLinkMessage')}
        backLabel={t('orders.detail.backToOrders')}
      />
    )
  }

  if (orderQuery.isLoading) {
    return (
      <div className="space-y-4" role="status" aria-live="polite">
        <p className="sr-only">{t('orders.detail.loading')}</p>
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
          title={t('orders.detail.unavailableTitle')}
          message={t('orders.detail.unavailableMessage')}
          backLabel={t('orders.detail.backToOrders')}
        />
      )
    }
    return (
      <div className={`space-y-3 ${adminAlertErrorClass}`}>
        <p role="alert">
          {orderQuery.error instanceof ApiClientError
            ? orderQuery.error.userMessage
            : t('orders.detail.loadError')}
        </p>
        <Button type="button" size="sm" onClick={() => void orderQuery.refetch()}>
          {tc('actions.retry')}
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
      <nav aria-label={t('shell.breadcrumb')} className="text-sm text-muted-foreground">
        <ol className="flex flex-wrap items-center gap-1.5">
          <li>
            <Link to="/admin/orders" className="font-medium text-primary hover:underline">
              {t('orders.detail.breadcrumbOrders')}
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li className="font-medium text-foreground">{t('orders.detail.breadcrumbDetail')}</li>
        </ol>
      </nav>

      <header className="space-y-3">
        <h1 className={adminPageTitleClass}>
          {t('orders.detail.title')}
        </h1>
        <p className="break-all font-mono text-xs text-muted-foreground">{order.id}</p>
        <div className="flex flex-wrap items-center gap-3">
          <OrderStatusBadge status={order.status} />
          <p className="text-sm text-muted-foreground">
            {t('orders.detail.placedUpdated', {
              placed: formatPlacedAt(order.placedAt),
              updated: formatPlacedAt(order.updatedAt),
            })}
          </p>
        </div>
        <p className="text-sm text-muted-foreground">
          {t('orders.detail.customerUserId')}{' '}
          <span className="font-mono text-xs text-foreground">{order.userId}</span>
        </p>
      </header>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)] lg:items-start">
        <div className="space-y-4">
          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle>
                <h2 className="text-base font-semibold">{t('orders.detail.itemsTitle')}</h2>
              </CardTitle>
              <CardDescription>
                {t('orders.detail.itemsDescription')}
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
                        {t('orders.detail.eachTimes', {
                          price: formatProductPrice(item.unitPrice, order.currency),
                          quantity: item.quantity,
                        })}
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
                <h2 className="text-base font-semibold">{t('orders.detail.shippingTitle')}</h2>
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
                  <h2 className="text-base font-semibold">{t('orders.detail.paymentTitle')}</h2>
                </CardTitle>
                <CardDescription>{t('orders.detail.paymentDescription')}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-2 text-sm text-muted-foreground">
                <p>
                  {t('orders.detail.paymentProviderStatus', {
                    provider: order.payment.provider,
                    status: order.payment.status,
                  })}
                  {order.payment.providerReference
                    ? t('orders.detail.paymentRef', { ref: order.payment.providerReference })
                    : ''}
                </p>
                <p>
                  {t('orders.detail.paymentAmount', {
                    amount: formatProductPrice(order.payment.amount, order.payment.currency),
                  })}
                </p>
                {order.payment.failureReason ? (
                  <p>{t('orders.detail.paymentFailure', { reason: order.payment.failureReason })}</p>
                ) : null}
                <p className="text-xs">
                  {t('orders.detail.paymentNote')}
                </p>
              </CardContent>
            </Card>
          ) : null}
        </div>

        <div className="space-y-4">
          <Card className="sticky top-20 shadow-sm">
            <CardHeader>
              <CardTitle>
                <h2 className="text-base font-semibold">{t('orders.detail.summaryTitle')}</h2>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <OrderPriceSummary
                currency={order.currency}
                subtotalAmount={order.subtotalAmount}
                shippingAmount={order.shippingAmount}
                discountAmount={order.discountAmount}
                totalAmount={order.totalAmount}
                appliedPromotions={order.appliedPromotions}
              />
            </CardContent>
          </Card>

          <Card className="shadow-sm">
            <CardHeader>
              <CardTitle>
                <h2 className="text-base font-semibold">{t('orders.detail.fulfillmentTitle')}</h2>
              </CardTitle>
              <CardDescription>
                {t('orders.detail.fulfillmentDescription')}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {transitions.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  {t('orders.detail.noTransitions')}
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
                          t('orders.detail.confirmChange', {
                            from: statusLabel(order.status),
                            to: statusLabel(nextStatus),
                          }),
                        )
                        if (!ok) return
                        setActionError(null)
                        setSuccessMessage(null)
                        statusMutation.mutate(nextStatus)
                      }}
                    >
                      {statusMutation.isPending && statusMutation.variables === nextStatus
                        ? tc('status.updating')
                        : t('orders.detail.setStatus', { status: statusLabel(nextStatus) })}
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
            <Link to="/admin/orders">{t('orders.detail.backToOrders')}</Link>
          </Button>
        </div>
      </div>
    </section>
  )
}

function Unavailable({
  title,
  message,
  backLabel,
}: {
  title: string
  message: string
  backLabel: string
}) {
  return (
    <Card className="border-dashed shadow-none">
      <CardContent className="flex flex-col items-center gap-3 px-6 py-10 text-center">
        <h1 className="text-xl font-semibold text-foreground">{title}</h1>
        <p className="text-sm text-muted-foreground">{message}</p>
        <Button asChild variant="link">
          <Link to="/admin/orders">{backLabel}</Link>
        </Button>
      </CardContent>
    </Card>
  )
}
