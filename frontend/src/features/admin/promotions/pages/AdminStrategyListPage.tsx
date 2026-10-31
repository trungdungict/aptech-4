import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { TagsIcon } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

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

/** Client-side page size — admin strategies API returns a full list (no server page contract). */
export const STRATEGY_LIST_PAGE_SIZE = 10

export function AdminStrategyListPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [statusError, setStatusError] = useState<string | null>(null)

  const pageRaw = Number(searchParams.get('page') ?? '0')
  const page = Number.isInteger(pageRaw) && pageRaw >= 0 ? pageRaw : 0

  const strategiesQuery = useQuery({
    queryKey: adminPromotionQueryKeys.strategies(),
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
      setStatusError(error instanceof ApiClientError ? error.userMessage : 'Status update failed.')
    },
  })

  const strategies = strategiesQuery.data
  const totalElements = strategies?.length ?? 0
  const totalPages = Math.max(1, Math.ceil(totalElements / STRATEGY_LIST_PAGE_SIZE))
  const safePage = Math.min(page, totalPages - 1)

  useEffect(() => {
    if (!strategies || page === safePage) {
      return
    }
    const next = new URLSearchParams(searchParams)
    if (safePage === 0) {
      next.delete('page')
    } else {
      next.set('page', String(safePage))
    }
    setSearchParams(next, { replace: true })
  }, [strategies, page, safePage, searchParams, setSearchParams])

  const pageItems = useMemo(() => {
    if (!strategies) {
      return []
    }
    const start = safePage * STRATEGY_LIST_PAGE_SIZE
    return strategies.slice(start, start + STRATEGY_LIST_PAGE_SIZE)
  }, [strategies, safePage])

  const goToPage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams)
    if (nextPage <= 0) {
      next.delete('page')
    } else {
      next.set('page', String(nextPage))
    }
    setSearchParams(next)
  }

  return (
    <section className={adminPageSectionClass}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-1">
          <nav aria-label="Breadcrumb" className="text-sm text-muted-foreground">
            <Link to="/admin/promotions" className="font-medium text-primary hover:underline">
              Promotions
            </Link>
            <span aria-hidden="true"> / </span>
            <span className="text-foreground">Strategies</span>
          </nav>
          <h1 className={adminPageTitleClass}>Promotion strategies</h1>
          <p className={adminPageLeadClass}>
            Reusable pricing/marketing templates. Soft-deactivate via status — campaigns keep their
            own runtime priority/stackable.
          </p>
        </div>
        <Button asChild>
          <Link to="/admin/promotions/strategies/new">New strategy</Link>
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
              : 'Unable to load strategies.'}
          </p>
          <Button type="button" size="sm" onClick={() => void strategiesQuery.refetch()}>
            Retry
          </Button>
        </div>
      ) : strategies && strategies.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <TagsIcon className="size-10 text-muted-foreground opacity-60" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">No strategies yet.</p>
            <Button asChild variant="link">
              <Link to="/admin/promotions/strategies/new">Create a strategy</Link>
            </Button>
          </CardContent>
        </Card>
      ) : strategies ? (
        <>
          <p className="text-sm text-muted-foreground" aria-live="polite">
            Showing page {safePage + 1} of {totalPages} ({totalElements} total)
          </p>
          <div className={adminTableWrapClass}>
            <table className={`${adminTableClass} min-w-[44rem]`}>
              <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                <tr>
                  <th className={adminThClass}>Name</th>
                  <th className={adminThClass}>Type</th>
                  <th className={adminThClass}>Config</th>
                  <th className={adminThClass}>Priority</th>
                  <th className={adminThClass}>Status</th>
                  <th className={adminThClass}>Actions</th>
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
                        {strategy.type === 'BANNER_ONLY' ? 'Banner only' : null}
                        {strategy.stackable ? ' · stackable' : ' · non-stackable'}
                      </td>
                      <td className={adminTdClass}>{strategy.priority}</td>
                      <td className={adminTdClass}>
                        <Badge variant={strategy.active ? 'default' : 'secondary'}>
                          {strategy.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                      <td className={`${adminTdClass} space-x-2`}>
                        <Button asChild variant="link" className="h-auto p-0">
                          <Link to={`/admin/promotions/strategies/${strategy.id}/edit`}>Edit</Link>
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
                                ? `Activate strategy “${strategy.name}”? Dependent campaigns may become placement/pricing effective when windows match.`
                                : `Deactivate strategy “${strategy.name}”? Dependent ACTIVE campaigns immediately lose pricing/placement effectiveness.`,
                            )
                            if (!ok) {
                              return
                            }
                            statusMutation.mutate({ id: strategy.id, active: next })
                          }}
                        >
                          {strategy.active ? 'Deactivate' : 'Activate'}
                        </Button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={safePage <= 0}
              onClick={() => goToPage(safePage - 1)}
            >
              Previous
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={safePage >= totalPages - 1}
              onClick={() => goToPage(safePage + 1)}
            >
              Next
            </Button>
          </div>
        </>
      ) : null}
    </section>
  )
}
