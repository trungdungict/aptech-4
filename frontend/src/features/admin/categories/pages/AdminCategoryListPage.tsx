import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FolderTreeIcon } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'

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

export function AdminCategoryListPage() {
  const queryClient = useQueryClient()
  const [statusError, setStatusError] = useState<string | null>(null)

  const categoriesQuery = useQuery({
    queryKey: adminCategoryQueryKeys.list(),
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
        error instanceof ApiClientError ? error.userMessage : 'Status update failed.',
      )
    },
  })

  const categories = categoriesQuery.data

  return (
    <section className={adminPageSectionClass}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-1">
          <h1 className={adminPageTitleClass}>
            Categories
          </h1>
          <p className={adminPageLeadClass}>
            Includes inactive categories. Soft-disable via status — no hard delete. Products require
            an active category when creating or updating.
          </p>
        </div>
        <Button asChild>
          <Link to="/admin/categories/new">New category</Link>
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
          <p className="sr-only">Loading categories…</p>
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-40 w-full rounded-xl" />
        </div>
      ) : categoriesQuery.isError ? (
        <div className={`space-y-3 ${adminAlertErrorClass}`}>
          <p role="alert">
            {categoriesQuery.error instanceof ApiClientError
              ? categoriesQuery.error.userMessage
              : 'Unable to load categories.'}
          </p>
          <Button type="button" size="sm" onClick={() => void categoriesQuery.refetch()}>
            Retry
          </Button>
        </div>
      ) : categories && categories.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <FolderTreeIcon className="size-10 text-muted-foreground opacity-60" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">No categories yet.</p>
            <Button asChild variant="link">
              <Link to="/admin/categories/new">Create a category</Link>
            </Button>
          </CardContent>
        </Card>
      ) : categories ? (
        <div className={adminTableWrapClass}>
          <table className={`${adminTableClass} min-w-[36rem]`}>
            <thead className="border-b border-border bg-muted/40 text-muted-foreground">
              <tr>
                <th className={adminThClass}>Name</th>
                <th className={adminThClass}>Slug</th>
                <th className={adminThClass}>Status</th>
                <th className={adminThClass}>Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {categories.map((category) => {
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
                        {category.active ? 'Active' : 'Inactive'}
                      </Badge>
                    </td>
                    <td className={adminTdClass}>
                      <div className="flex flex-wrap gap-2">
                        <Button asChild variant="link" size="sm" className="h-auto px-0">
                          <Link to={`/admin/categories/${category.id}/edit`}>Edit</Link>
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
                                ? `Activate “${category.name}”?`
                                : `Deactivate “${category.name}”? Soft-disable only — the category is not deleted. Products may reject assignment to inactive categories.`,
                            )
                            if (!ok) return
                            setStatusError(null)
                            statusMutation.mutate({ id: category.id, active: next })
                          }}
                        >
                          {pending
                            ? 'Updating…'
                            : category.active
                              ? 'Deactivate'
                              : 'Activate'}
                        </Button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      ) : null}
    </section>
  )
}
