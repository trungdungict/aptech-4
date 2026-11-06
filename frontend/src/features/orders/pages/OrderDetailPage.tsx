import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ImageIcon } from 'lucide-react'
import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link, useLocation, useParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { ProductPrice } from '@/features/catalog/components/ProductPrice'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import type { CheckoutResult } from '@/features/checkout/types'
import { useAppCurrency } from '@/features/i18n/hooks/useAppCurrency'
import { ApiClientError } from '@/lib/api/api-error'
import { formatDateTime } from '@/lib/i18n'

import * as ordersApi from '../api/orders-api'
import { OrderPriceSummary } from '../components/OrderPriceSummary'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { orderQueryKeys } from '../hooks/order-query-keys'
import { hasOrderDiscount } from '../lib/order-money'

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function OrderDetailPage() {
  const { t } = useTranslation('orders')
  const { t: tc } = useTranslation('common')
  const { currency: displayCurrency } = useAppCurrency()
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
      <nav aria-label={t('detail.breadcrumbAria')} className="text-sm text-muted-foreground">
        <ol className="flex flex-wrap items-center gap-1.5">
          <li>
            <Link to="/orders" className="font-medium text-primary hover:underline">
              {t('history.title')}
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li className="font-medium text-foreground">{tc('nav.detail')}</li>
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
            <Trans
              i18nKey="detail.flashSummary"
              ns="orders"
              values={{
                orderId: checkoutFlash.orderId,
                status: checkoutFlash.orderStatus,
                payment: checkoutFlash.payment
                  ? t('detail.flashPayment', { status: checkoutFlash.payment.status })
                  : '',
              }}
              components={{ mono: <span className="font-mono text-xs" />, strong: <strong /> }}
            />
          </p>
          <p className="mt-1 text-xs opacity-90">
            {t('detail.flashDemo', {
              cleared: checkoutFlash.cartCleared ? t('detail.yes') : t('detail.no'),
            })}
            {hasOrderDiscount(checkoutFlash.discountAmount)
              ? t('detail.flashDiscount', {
                  amount: formatProductPrice(
                    checkoutFlash.discountAmount,
                    checkoutFlash.currency,
                    undefined,
                    displayCurrency,
                  ),
                })
              : ''}
          </p>
          <p className="mt-1 text-xs opacity-90">{t('detail.flashLive')}</p>
        </div>
      ) : null}

      {!idValid ? (
        <Unavailable title={t('detail.invalidLink')} message={t('detail.invalidLinkMessage')} />
      ) : orderQuery.isLoading ? (
        <OrderDetailSkeleton />
      ) : orderQuery.isError ? (
        orderQuery.error instanceof ApiClientError && orderQuery.error.status === 404 ? (
          <Unavailable
            title={t('detail.unavailable')}
            message={t('detail.unavailableMessage')}
          />
        ) : (
          <div className="space-y-3 rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
            <p role="alert">
              {orderQuery.error instanceof ApiClientError
                ? orderQuery.error.userMessage
                : t('detail.loadError')}
            </p>
            <Button type="button" size="sm" onClick={() => void orderQuery.refetch()}>
              {tc('actions.retry')}
            </Button>
          </div>
        )
      ) : order ? (
        <article className="space-y-6">
          <header className="space-y-3">
            <h1 className="text-2xl font-semibold tracking-tight text-foreground md:text-3xl">
              {t('detail.title')}
            </h1>
            <p className="break-all font-mono text-xs text-muted-foreground">{order.id}</p>
            <div className="flex flex-wrap items-center gap-3">
              <OrderStatusBadge status={order.status} />
              <p className="text-sm text-muted-foreground">
                {t('detail.placed', { when: formatDateTime(order.placedAt) })}
              </p>
            </div>
          </header>

          <div className="grid gap-6 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)] lg:items-start">
            <div className="space-y-4">
              <Card className="shadow-sm">
                <CardHeader>
                  <CardTitle>
                    <h2 className="text-base font-semibold">{t('detail.itemsSnapshot')}</h2>
                  </CardTitle>
                  <CardDescription>
                    {t('detail.itemsSnapshotDesc')}
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
                            {t('detail.eachTimes', {
                              price: formatProductPrice(
                                item.unitPrice,
                                order.currency,
                                undefined,
                                displayCurrency,
                              ),
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
                    <h2 className="text-base font-semibold">{t('detail.shipping')}</h2>
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
                      <h2 className="text-base font-semibold">{t('detail.paymentMock')}</h2>
                    </CardTitle>
                    <CardDescription>{t('detail.paymentDesc')}</CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-1 text-sm text-muted-foreground">
                    <p>
                      {t('detail.paymentLine', {
                        provider: order.payment.provider,
                        status: order.payment.status,
                      })}
                      {order.payment.providerReference
                        ? t('detail.paymentRef', { ref: order.payment.providerReference })
                        : ''}
                    </p>
                    <p>
                      {t('detail.paymentAmount', {
                        amount: formatProductPrice(
                          order.payment.amount,
                          order.payment.currency,
                          undefined,
                          displayCurrency,
                        ),
                      })}
                    </p>
                    {order.payment.failureReason ? (
                      <p>{t('detail.paymentFailure', { reason: order.payment.failureReason })}</p>
                    ) : null}
                  </CardContent>
                </Card>
              ) : null}
            </div>

            <div className="space-y-4">
              <Card className="sticky top-20 shadow-sm">
                <CardHeader>
                  <CardTitle>
                    <h2 className="text-base font-semibold">{t('detail.summaryTitle')}</h2>
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

              {canCancel ? (
                <Card className="shadow-sm">
                  <CardHeader>
                    <CardTitle>
                      <h2 className="text-base font-semibold">{t('detail.cancel')}</h2>
                    </CardTitle>
                    <CardDescription>
                      {t('detail.cancelDesc')}
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-3">
                    {cancelMutation.isError ? (
                      <p className="text-sm text-destructive" role="alert">
                        {cancelMutation.error instanceof ApiClientError
                          ? cancelMutation.error.userMessage
                          : t('detail.cancelFailed')}
                      </p>
                    ) : null}

                    {!confirmCancel ? (
                      <Button
                        type="button"
                        variant="outline"
                        disabled={cancelMutation.isPending}
                        onClick={() => setConfirmCancel(true)}
                      >
                        {t('detail.cancel')}
                      </Button>
                    ) : (
                      <div
                        className="space-y-3 rounded-xl border border-border bg-muted/40 p-3"
                        role="alertdialog"
                        aria-labelledby="cancel-order-title"
                        aria-describedby="cancel-order-desc"
                      >
                        <p id="cancel-order-title" className="text-sm font-medium text-foreground">
                          {t('detail.confirmCancel')}
                        </p>
                        <p id="cancel-order-desc" className="text-xs text-muted-foreground">
                          {t('detail.cancelConfirmDesc')}
                        </p>
                        <div className="flex flex-wrap gap-2">
                          <Button
                            type="button"
                            variant="destructive"
                            disabled={cancelMutation.isPending}
                            onClick={() => cancelMutation.mutate()}
                          >
                            {cancelMutation.isPending ? t('detail.cancelling') : t('detail.confirmYes')}
                          </Button>
                          <Button
                            type="button"
                            variant="outline"
                            disabled={cancelMutation.isPending}
                            onClick={() => setConfirmCancel(false)}
                          >
                            {t('detail.keep')}
                          </Button>
                        </div>
                      </div>
                    )}
                  </CardContent>
                </Card>
              ) : null}

              <Button asChild variant="outline" className="w-full sm:w-auto">
                <Link to="/orders">{t('detail.back')}</Link>
              </Button>
            </div>
          </div>
        </article>
      ) : null}
    </section>
  )
}

function OrderDetailSkeleton() {
  const { t } = useTranslation('orders')
  return (
    <div className="space-y-4" role="status" aria-live="polite">
      <p className="sr-only">{t('detail.loadingSr')}</p>
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
  const { t } = useTranslation('orders')
  return (
    <Card className="border-dashed shadow-none">
      <CardContent className="flex flex-col items-center gap-3 px-6 py-10 text-center">
        <h1 className="text-xl font-semibold text-foreground">{title}</h1>
        <p className="text-sm text-muted-foreground">{message}</p>
        <Button asChild variant="link">
          <Link to="/orders">{t('detail.back')}</Link>
        </Button>
      </CardContent>
    </Card>
  )
}
