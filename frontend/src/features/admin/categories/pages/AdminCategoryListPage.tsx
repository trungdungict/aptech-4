import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { catalogQueryKeys } from '@/features/catalog/hooks/catalog-query-keys'
import { ApiClientError } from '@/lib/api/api-error'

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
    <section className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">Categories</h1>
          <p className="text-sm text-muted-foreground">
            Includes inactive categories. Soft-disable via status — no hard delete. Products require
            an active category when creating or updating.
          </p>
        </div>
        <Link
          to="/admin/categories/new"
          className="inline-flex rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
        >
          New category
        </Link>
      </div>

      {statusError ? (
        <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
          {statusError}
        </p>
      ) : null}

      {categoriesQuery.isLoading ? (
        <p className="text-sm text-muted-foreground" role="status">
          Loading categories…
        </p>
      ) : categoriesQuery.isError ? (
        <div className="space-y-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          <p role="alert">
            {categoriesQuery.error instanceof ApiClientError
              ? categoriesQuery.error.userMessage
              : 'Unable to load categories.'}
          </p>
          <button
            type="button"
            className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground"
            onClick={() => void categoriesQuery.refetch()}
          >
            Retry
          </button>
        </div>
      ) : categories && categories.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-card px-6 py-10 text-center">
          <p className="text-sm text-muted-foreground">No categories yet.</p>
          <Link
            to="/admin/categories/new"
            className="mt-4 inline-flex text-sm font-medium text-primary underline-offset-4 hover:underline"
          >
            Create a category
          </Link>
        </div>
      ) : categories ? (
        <div className="overflow-x-auto rounded-2xl border border-border bg-card">
          <table className="w-full min-w-[36rem] text-left text-sm">
            <thead className="border-b border-border bg-muted/40 text-muted-foreground">
              <tr>
                <th className="px-4 py-3 font-medium">Name</th>
                <th className="px-4 py-3 font-medium">Slug</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {categories.map((category) => {
                const pending =
                  statusMutation.isPending && statusMutation.variables?.id === category.id
                return (
                  <tr key={category.id}>
                    <td className="px-4 py-3 font-medium text-foreground">{category.name}</td>
                    <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                      {category.slug}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={
                          category.active
                            ? 'font-medium text-emerald-800'
                            : 'font-medium text-amber-800'
                        }
                      >
                        {category.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-2">
                        <Link
                          to={`/admin/categories/${category.id}/edit`}
                          className="font-medium text-primary underline-offset-4 hover:underline"
                        >
                          Edit
                        </Link>
                        <button
                          type="button"
                          disabled={pending}
                          className="font-medium text-foreground underline-offset-4 hover:underline disabled:opacity-60"
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
                        </button>
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
