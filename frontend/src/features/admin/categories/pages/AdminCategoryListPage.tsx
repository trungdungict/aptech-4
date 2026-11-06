import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FolderTreeIcon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { DEFAULT_LIST_PAGE_SIZE, Pagination, useClientListPagination } from '@/components/pagination'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { catalogQueryKeys } from '@/features/catalog/hooks/catalog-query-keys'
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

import * as categoriesApi from '../api/admin-categories-api'
import { adminCategoryQueryKeys } from '../hooks/admin-category-query-keys'
import { useAppLocale } from '@/features/i18n/hooks/useAppLocale'

/** Client-side page size — admin categories API returns a full list (no server page contract). */
export const CATEGORY_LIST_PAGE_SIZE = DEFAULT_LIST_PAGE_SIZE

export function AdminCategoryListPage() {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const queryClient = useQueryClient()
  const { locale } = useAppLocale()
  const [statusError, setStatusError] = useState<string | null>(null)

  const categoriesQuery = useQuery({
    queryKey: adminCategoryQueryKeys.list(locale),
    queryFn: ({ signal }) => categoriesApi.listAdminCategories(signal),
    retry: false,
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      categoriesApi.updateAdminCategoryStatus(id, { active }),
    onSuccess: async () => {
      setStatusError(null)
      await queryClient.invalidateQueries({ queryKey: adminCategoryQueryKeys.all })
      await queryClient.invalidateQueries({ queryKey: catalogQueryKeys.all })
    },
    onError: (error) => {
      setStatusError(
        error instanceof ApiClientError ? error.userMessage : t('categories.list.statusFailed'),
      )
    },
  })

  const categories = categoriesQuery.data
  const { page, totalPages, totalElements, pageItems, goToPage } = useClientListPagination(
    categories,
    CATEGORY_LIST_PAGE_SIZE,
  )

  return (
    <section className={adminPageSectionClass}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-1">
          <h1 className={adminPageTitleClass}>
            {t('categories.list.title')}
          </h1>
          <p className={adminPageLeadClass}>
            {t('categories.list.lead')}
          </p>
        </div>
        <Button asChild>
          <Link to="/admin/categories/new">{t('categories.list.newCategory')}</Link>
        </Button>
      </div>

      {statusError ? (
        <p
          className={adminAlertErrorClass}
          role="alert"
        >
          {statusError}
        </p>
      ) : null}

      {categoriesQuery.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <p className="sr-only">{t('categories.list.loading')}</p>
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-40 w-full rounded-xl" />
        </div>
      ) : categoriesQuery.isError ? (
        <div className={`space-y-3 ${adminAlertErrorClass}`}>
          <p role="alert">
            {categoriesQuery.error instanceof ApiClientError
              ? categoriesQuery.error.userMessage
              : t('categories.list.loadError')}
          </p>
          <Button type="button" size="sm" onClick={() => void categoriesQuery.refetch()}>
            {tc('actions.retry')}
          </Button>
        </div>
      ) : categories && categories.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <FolderTreeIcon className="size-10 text-muted-foreground opacity-60" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">{t('categories.list.empty')}</p>
            <Button asChild variant="link">
              <Link to="/admin/categories/new">{t('categories.list.createCategory')}</Link>
            </Button>
          </CardContent>
        </Card>
      ) : categories ? (
        <>
          <div className={adminTableWrapClass}>
            <table className={`${adminTableClass} min-w-[36rem]`}>
              <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                <tr>
                  <th className={adminThClass}>{t('categories.list.columns.name')}</th>
                  <th className={adminThClass}>{t('categories.list.columns.slug')}</th>
                  <th className={adminThClass}>{t('categories.list.columns.status')}</th>
                  <th className={adminThClass}>{t('categories.list.columns.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {pageItems.map((category) => {
                  const pending =
                    statusMutation.isPending && statusMutation.variables?.id === category.id
                  return (
                    <tr key={category.id} className="hover:bg-muted/30">
                      <td className={`${adminTdClass} font-medium text-foreground`}>{category.name}</td>
                      <td className={`${adminTdClass} font-mono text-xs text-muted-foreground`}>
                        {category.slug}
                      </td>
                      <td className={adminTdClass}>
                        <Badge variant={category.active ? 'success' : 'warning'}>
                          {category.active ? t('common.active') : t('common.inactive')}
                        </Badge>
                      </td>
                      <td className={adminTdClass}>
                        <div className="flex flex-wrap gap-2">
                          <Button asChild variant="link" size="sm" className="h-auto px-0">
                            <Link to={`/admin/categories/${category.id}/edit`}>{t('categories.list.edit')}</Link>
                          </Button>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            disabled={pending}
                            onClick={() => {
                              const next = !category.active
                              const ok = window.confirm(
                                next
                                  ? t('categories.list.confirmActivate', { name: category.name })
                                  : t('categories.list.confirmDeactivate', { name: category.name }),
                              )
                              if (!ok) return
                              setStatusError(null)
                              statusMutation.mutate({ id: category.id, active: next })
                            }}
                          >
                            {pending
                              ? tc('status.updating')
                              : category.active
                                ? t('categories.list.deactivate')
                                : t('categories.list.activate')}
                          </Button>
                        </div>
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
            pageSize={CATEGORY_LIST_PAGE_SIZE}
            itemLabel={t('pagination.itemLabels.categories')}
            onPageChange={goToPage}
          />
        </>
      ) : null}
    </section>
  )
}
