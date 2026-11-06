import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { TagsIcon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { DEFAULT_LIST_PAGE_SIZE, Pagination, useClientListPagination } from '@/components/pagination'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiClientError } from '@/lib/api/api-error'

import {
  adminAlertErrorClass,
  adminPageLeadClass,
  adminPageSectionClass,
  adminPageTitleClass,
  adminTableClass,
  adminTableWrapClass,
  adminTdClass,
  adminThClass,
} from '../../lib/admin-ui'
import * as api from '../api/admin-promotions-api'
import { adminPromotionQueryKeys } from '../hooks/admin-promotion-query-keys'
import { useAppLocale } from '@/features/i18n/hooks/useAppLocale'

/** Client-side page size — admin strategies API returns a full list (no server page contract). */
export const STRATEGY_LIST_PAGE_SIZE = DEFAULT_LIST_PAGE_SIZE

export function AdminStrategyListPage() {
  const queryClient = useQueryClient()
  const { locale } = useAppLocale()
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const [statusError, setStatusError] = useState<string | null>(null)

  const strategiesQuery = useQuery({
    queryKey: adminPromotionQueryKeys.strategies(locale),
    queryFn: ({ signal }) => api.listAdminStrategies(signal),
    retry: false,
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      api.updateAdminStrategyStatus(id, active),
    onSuccess: async () => {
      setStatusError(null)
      await queryClient.invalidateQueries({ queryKey: adminPromotionQueryKeys.all })
    },
    onError: (error) => {
      setStatusError(error instanceof ApiClientError ? error.userMessage : t('promotions.strategies.list.statusFailed'))
    },
  })

  const strategies = strategiesQuery.data
  const { page, totalPages, totalElements, pageItems, goToPage } = useClientListPagination(
    strategies,
    STRATEGY_LIST_PAGE_SIZE,
  )

  return (
    <section className={adminPageSectionClass}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-1">
          <nav aria-label={t('shell.breadcrumb')} className="text-sm text-muted-foreground">
            <Link to="/admin/promotions" className="font-medium text-primary hover:underline">
              {t('promotions.breadcrumb.promotions')}
            </Link>
            <span aria-hidden="true"> / </span>
            <span className="text-foreground">{t('promotions.breadcrumb.strategies')}</span>
          </nav>
          <h1 className={adminPageTitleClass}>{t('promotions.strategies.list.title')}</h1>
          <p className={adminPageLeadClass}>
            {t('promotions.strategies.list.lead')}
          </p>
        </div>
        <Button asChild>
          <Link to="/admin/promotions/strategies/new">{t('promotions.strategies.list.newStrategy')}</Link>
        </Button>
      </div>

      {statusError ? (
        <p className={adminAlertErrorClass} role="alert">
          {statusError}
        </p>
      ) : null}

      {strategiesQuery.isLoading ? (
        <div className="space-y-3" role="status">
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-40 w-full rounded-xl" />
        </div>
      ) : strategiesQuery.isError ? (
        <div className={`space-y-3 ${adminAlertErrorClass}`}>
          <p role="alert">
            {strategiesQuery.error instanceof ApiClientError
              ? strategiesQuery.error.userMessage
              : t('promotions.strategies.list.loadError')}
          </p>
          <Button type="button" size="sm" onClick={() => void strategiesQuery.refetch()}>
            {tc('actions.retry')}
          </Button>
        </div>
      ) : strategies && strategies.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <TagsIcon className="size-10 text-muted-foreground opacity-60" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">{t('promotions.strategies.list.empty')}</p>
            <Button asChild variant="link">
              <Link to="/admin/promotions/strategies/new">{t('promotions.strategies.list.createStrategy')}</Link>
            </Button>
          </CardContent>
        </Card>
      ) : strategies ? (
        <>
          <div className={adminTableWrapClass}>
            <table className={`${adminTableClass} min-w-[44rem]`}>
              <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                <tr>
                  <th className={adminThClass}>{t('promotions.strategies.list.columns.name')}</th>
                  <th className={adminThClass}>{t('promotions.strategies.list.columns.type')}</th>
                  <th className={adminThClass}>{t('promotions.strategies.list.columns.config')}</th>
                  <th className={adminThClass}>{t('promotions.strategies.list.columns.priority')}</th>
                  <th className={adminThClass}>{t('promotions.strategies.list.columns.status')}</th>
                  <th className={adminThClass}>{t('promotions.strategies.list.columns.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {pageItems.map((strategy) => {
                  const pending = statusMutation.isPending && statusMutation.variables?.id === strategy.id
                  return (
                    <tr key={strategy.id}>
                      <td className={adminTdClass}>
                        <div className="font-medium text-foreground">{strategy.name}</div>
                        {strategy.code ? (
                          <div className="font-mono text-xs text-muted-foreground">{strategy.code}</div>
                        ) : null}
                      </td>
                      <td className={adminTdClass}>
                        <Badge variant="outline">{strategy.type}</Badge>
                      </td>
                      <td className={`${adminTdClass} text-xs text-muted-foreground`}>
                        {strategy.discountPercent != null ? `${strategy.discountPercent}%` : null}
                        {strategy.discountAmount != null ? ` ${strategy.discountAmount}` : null}
                        {strategy.type === 'BANNER_ONLY' ? t('promotions.strategies.list.bannerOnly') : null}
                        {strategy.stackable
                          ? t('promotions.strategies.list.stackable')
                          : t('promotions.strategies.list.nonStackable')}
                      </td>
                      <td className={adminTdClass}>{strategy.priority}</td>
                      <td className={adminTdClass}>
                        <Badge variant={strategy.active ? 'default' : 'secondary'}>
                          {strategy.active ? t('promotions.strategies.list.active') : t('promotions.strategies.list.inactive')}
                        </Badge>
                      </td>
                      <td className={`${adminTdClass} space-x-2`}>
                        <Button asChild variant="link" className="h-auto p-0">
                          <Link to={`/admin/promotions/strategies/${strategy.id}/edit`}>{t('promotions.strategies.list.edit')}</Link>
                        </Button>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={pending}
                          onClick={() => {
                            const next = !strategy.active
                            const ok = window.confirm(
                              next
                                ? t('promotions.strategies.list.confirmActivate', { name: strategy.name })
                                : t('promotions.strategies.list.confirmDeactivate', { name: strategy.name }),
                            )
                            if (!ok) {
                              return
                            }
                            statusMutation.mutate({ id: strategy.id, active: next })
                          }}
                        >
                          {strategy.active ? t('promotions.strategies.list.deactivate') : t('promotions.strategies.list.activate')}
                        </Button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            pageSize={STRATEGY_LIST_PAGE_SIZE}
            itemLabel={t('pagination.itemLabels.strategies')}
            onPageChange={goToPage}
          />
        </>
      ) : null}
    </section>
  )
}
