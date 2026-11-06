import { useQuery } from '@tanstack/react-query'
import { PackageIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router-dom'

import { Pagination, parsePageParam, writePageParam } from '@/components/pagination'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { ProductPrice } from '@/features/catalog/components/ProductPrice'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { useAppCurrency } from '@/features/i18n/hooks/useAppCurrency'
import { ApiClientError } from '@/lib/api/api-error'
import { formatDateTime } from '@/lib/i18n'

import * as ordersApi from '../api/orders-api'
import { OrderStatusBadge } from '../components/OrderStatusBadge'
import { orderQueryKeys } from '../hooks/order-query-keys'
import { hasOrderDiscount } from '../lib/order-money'
import { ORDER_DEFAULT_PAGE_SIZE, ORDER_DEFAULT_SORT } from '../types'

export function OrderHistoryPage() {
  const { t } = useTranslation('orders')
  const { t: tc } = useTranslation('common')
  const { currency: displayCurrency } = useAppCurrency()
  const { user } = useAuth()
  const userId = user?.id ?? ''
  const [searchParams, setSearchParams] = useSearchParams()
  const page = parsePageParam(searchParams.get('page'))

  const listParams = {
    page,
    size: ORDER_DEFAULT_PAGE_SIZE,
    sort: ORDER_DEFAULT_SORT,
  }

  const goToPage = (nextPage: number) => {
    setSearchParams(writePageParam(searchParams, nextPage))
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
        <h1 className="text-3xl font-semibold tracking-tight text-foreground">{t('history.title')}</h1>
        <p className="text-sm text-muted-foreground">
          {t('history.subtitle')}
        </p>
      </div>

      {ordersQuery.isLoading ? (
        <OrderHistorySkeleton />
      ) : ordersQuery.isError ? (
        <div className="space-y-3 rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <p role="alert">
            {ordersQuery.error instanceof ApiClientError
              ? ordersQuery.error.status === 403
                ? t('history.customerOnly')
                : ordersQuery.error.userMessage
              : t('history.loadError')}
          </p>
          <Button type="button" size="sm" onClick={() => void ordersQuery.refetch()}>
            {tc('actions.retry')}
          </Button>
        </div>
      ) : data && data.items.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <PackageIcon className="size-10 text-muted-foreground opacity-60" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">{t('history.empty')}</p>
            <Button asChild>
              <Link to="/products">{t('history.browseCatalog')}</Link>
            </Button>
          </CardContent>
        </Card>
      ) : data ? (
        <>
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
                        {t('history.orderLabel')}{' '}
                        <span className="font-mono text-sm">{order.id}</span>
                      </p>
                      <p className="text-sm text-muted-foreground">
                        {formatDateTime(order.placedAt)}
                      </p>
                      <OrderStatusBadge status={order.status} />
                    </div>
                    <div className="space-y-1 text-right">
                      <ProductPrice
                        unitPrice={order.totalAmount}
                        currency={order.currency}
                        className="text-base font-semibold text-foreground"
                      />
                      {hasOrderDiscount(order.discountAmount) ? (
                        <p className="text-xs text-emerald-700">
                          {t('history.includesDiscount', {
                            amount: formatProductPrice(
                              order.discountAmount,
                              order.currency,
                              undefined,
                              displayCurrency,
                            ),
                          })}
                        </p>
                      ) : null}
                    </div>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            pageSize={data.size}
            itemLabel={t('history.results')}
            disabled={ordersQuery.isFetching}
            onPageChange={goToPage}
          />
        </>
      ) : null}
    </section>
  )
}

function OrderHistorySkeleton() {
  const { t } = useTranslation('orders')
  return (
    <div className="space-y-3" role="status" aria-live="polite">
      <p className="sr-only">{t('history.loadingSr')}</p>
      {Array.from({ length: 3 }, (_, i) => (
        <Skeleton key={i} className="h-24 w-full rounded-xl" />
      ))}
    </div>
  )
}
