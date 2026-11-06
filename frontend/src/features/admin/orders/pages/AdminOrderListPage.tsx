import { useQuery } from '@tanstack/react-query'
import { ClipboardListIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router-dom'

import { Pagination, parsePageParam, writePageParam } from '@/components/pagination'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { OrderStatusBadge } from '@/features/orders/components/OrderStatusBadge'
import { ORDER_DEFAULT_PAGE_SIZE, ORDER_DEFAULT_SORT } from '@/features/orders/types'
import { ApiClientError } from '@/lib/api/api-error'
import { formatDateTime } from '@/lib/i18n'

import {
  adminAlertErrorClass,
  adminNativeSelectClass,
  adminPageLeadClass,
  adminPageSectionClass,
  adminPageTitleClass,
  adminTableClass,
  adminTableWrapClass,
  adminTdClass,
  adminThClass,
} from '../../lib/admin-ui'

import * as ordersApi from '../api/admin-orders-api'
import { adminOrderQueryKeys } from '../hooks/admin-order-query-keys'
import { ORDER_STATUS_VALUES } from '../lib/admin-order-transitions'
import { useOrderStatusLabel } from '../lib/use-order-status-label'

function formatPlacedAt(iso: string): string {
  return formatDateTime(iso, { dateStyle: 'medium', timeStyle: 'short' })
}

/** `value` is the API sort token (never translated); `labelKey` is an `admin` i18n key. */
const SORT_OPTIONS = [
  { value: 'placedAt,desc', labelKey: 'orders.list.sortOptions.placedDesc' },
  { value: 'placedAt,asc', labelKey: 'orders.list.sortOptions.placedAsc' },
  { value: 'totalAmount,desc', labelKey: 'orders.list.sortOptions.totalDesc' },
  { value: 'totalAmount,asc', labelKey: 'orders.list.sortOptions.totalAsc' },
  { value: 'status,asc', labelKey: 'orders.list.sortOptions.statusAsc' },
  { value: 'status,desc', labelKey: 'orders.list.sortOptions.statusDesc' },
] as const

export function AdminOrderListPage() {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const statusLabel = useOrderStatusLabel()
  const [searchParams, setSearchParams] = useSearchParams()
  const page = parsePageParam(searchParams.get('page'))
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

  const goToPage = (nextPage: number) => {
    setSearchParams(writePageParam(searchParams, nextPage))
  }

  const ordersQuery = useQuery({
    queryKey: adminOrderQueryKeys.list(listParams),
    queryFn: ({ signal }) => ordersApi.listAdminOrders(listParams, signal),
    retry: false,
  })

  const data = ordersQuery.data

  return (
    <section className={adminPageSectionClass}>
      <div className="space-y-1">
        <h1 className={adminPageTitleClass}>
          {t('orders.list.title')}
        </h1>
        <p className={adminPageLeadClass}>
          {t('orders.list.lead')}
        </p>
      </div>

      <Card className="shadow-sm">
        <CardContent className="pt-6">
          <form
            className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end"
            onSubmit={(event) => {
              event.preventDefault()
              const form = new FormData(event.currentTarget)
              const next = new URLSearchParams()
              const nextStatus = String(form.get('status') ?? '')
              const nextSort = String(form.get('sort') ?? ORDER_DEFAULT_SORT)
              if (nextStatus) next.set('status', nextStatus)
              next.set('sort', nextSort)
              setSearchParams(next)
            }}
          >
            <div className="space-y-1.5">
              <Label htmlFor="admin-order-status">{t('orders.list.statusFilter')}</Label>
              <select
                id="admin-order-status"
                name="status"
                defaultValue={status}
                className={adminNativeSelectClass}
              >
                <option value="">{t('orders.list.allStatuses')}</option>
                {ORDER_STATUS_VALUES.map((value) => (
                  <option key={value} value={value}>
                    {statusLabel(value)}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="admin-order-sort">{t('orders.list.sort')}</Label>
              <select
                id="admin-order-sort"
                name="sort"
                defaultValue={sort}
                className={adminNativeSelectClass}
              >
                {SORT_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {t(option.labelKey)}
                  </option>
                ))}
              </select>
            </div>
            <Button type="submit" variant="outline">
              {t('orders.list.apply')}
            </Button>
          </form>
        </CardContent>
      </Card>

      {ordersQuery.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <p className="sr-only">{t('orders.list.loading')}</p>
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-48 w-full rounded-xl" />
        </div>
      ) : ordersQuery.isError ? (
        <div className={`space-y-3 ${adminAlertErrorClass}`}>
          <p role="alert">
            {ordersQuery.error instanceof ApiClientError
              ? ordersQuery.error.userMessage
              : t('orders.list.loadError')}
          </p>
          <Button type="button" size="sm" onClick={() => void ordersQuery.refetch()}>
            {tc('actions.retry')}
          </Button>
        </div>
      ) : data && data.items.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <ClipboardListIcon
              className="size-10 text-muted-foreground opacity-60"
              aria-hidden="true"
            />
            <p className="text-sm text-muted-foreground">{t('orders.list.empty')}</p>
          </CardContent>
        </Card>
      ) : data ? (
        <>
          <div className={adminTableWrapClass}>
            <table className={`${adminTableClass} min-w-[44rem]`}>
              <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                <tr>
                  <th className={adminThClass}>{t('orders.list.columns.order')}</th>
                  <th className={adminThClass}>{t('orders.list.columns.userId')}</th>
                  <th className={adminThClass}>{t('orders.list.columns.placed')}</th>
                  <th className={adminThClass}>{t('orders.list.columns.status')}</th>
                  <th className={adminThClass}>{t('orders.list.columns.total')}</th>
                  <th className={adminThClass}>{t('orders.list.columns.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.items.map((order) => (
                  <tr key={order.id} className="hover:bg-muted/30">
                    <td className={adminTdClass}>
                      <Link
                        to={`/admin/orders/${order.id}`}
                        className="font-mono text-xs font-medium text-primary underline-offset-4 hover:underline"
                      >
                        {order.id}
                      </Link>
                    </td>
                    <td className={`${adminTdClass} font-mono text-xs text-muted-foreground`}>
                      {order.userId}
                    </td>
                    <td className={`${adminTdClass} text-muted-foreground`}>
                      {formatPlacedAt(order.placedAt)}
                    </td>
                    <td className={adminTdClass}>
                      <OrderStatusBadge status={order.status} />
                    </td>
                    <td className={`${adminTdClass} font-semibold text-foreground`}>
                      {formatProductPrice(order.totalAmount, order.currency)}
                    </td>
                    <td className={adminTdClass}>
                      <Button asChild variant="link" size="sm" className="h-auto px-0">
                        <Link to={`/admin/orders/${order.id}`}>{t('orders.list.view')}</Link>
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            pageSize={data.size}
            itemLabel={t('pagination.itemLabels.orders')}
            disabled={ordersQuery.isFetching}
            onPageChange={goToPage}
          />
        </>
      ) : null}
    </section>
  )
}
