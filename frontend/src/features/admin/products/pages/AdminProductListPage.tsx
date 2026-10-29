import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { listAdminCategories } from '@/features/admin/categories/api/admin-categories-api'
import { adminCategoryQueryKeys } from '@/features/admin/categories/hooks/admin-category-query-keys'
import { catalogQueryKeys } from '@/features/catalog/hooks/catalog-query-keys'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { PRODUCT_DEFAULT_PAGE_SIZE, PRODUCT_DEFAULT_SORT } from '@/features/catalog/types'
import { ApiClientError } from '@/lib/api/api-error'

import * as productsApi from '../api/admin-products-api'
import { adminProductQueryKeys } from '../hooks/admin-product-query-keys'

export function AdminProductListPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [statusError, setStatusError] = useState<string | null>(null)

  const pageRaw = Number(searchParams.get('page') ?? '0')
  const page = Number.isInteger(pageRaw) && pageRaw >= 0 ? pageRaw : 0
  const q = searchParams.get('q') ?? ''
  const activeParam = searchParams.get('active')
  const active =
    activeParam === 'true' ? true : activeParam === 'false' ? false : undefined

  const listParams = {
    q: q || undefined,
    active,
    page,
    size: PRODUCT_DEFAULT_PAGE_SIZE,
    sort: PRODUCT_DEFAULT_SORT,
  }

  const productsQuery = useQuery({
    queryKey: adminProductQueryKeys.list(listParams),
    queryFn: ({ signal }) => productsApi.listAdminProducts(listParams, signal),
    retry: false,
  })

  const categoriesQuery = useQuery({
    queryKey: adminCategoryQueryKeys.list(),
    queryFn: ({ signal }) => listAdminCategories(signal),
    retry: false,
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, active: next }: { id: string; active: boolean }) =>
      productsApi.updateAdminProductStatus(id, { active: next }),
    onSuccess: async () => {
      setStatusError(null)
      await queryClient.invalidateQueries({ queryKey: adminProductQueryKeys.all })
      await queryClient.invalidateQueries({ queryKey: catalogQueryKeys.all })
    },
    onError: (error) => {
      setStatusError(
        error instanceof ApiClientError ? error.userMessage : 'Status update failed.',
      )
    },
  })

  const data = productsQuery.data

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">Products</h1>
          <p className="text-sm text-muted-foreground">
            Admin list includes inactive products. Soft-disable via status — no hard delete.
            Inventory cannot be changed here.
          </p>
        </div>
        <Link
          to="/admin/products/new"
          className="inline-flex rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
        >
          New product
        </Link>
      </div>

      <form
        className="flex flex-wrap gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          const form = new FormData(event.currentTarget)
          const next = new URLSearchParams(searchParams)
          const nextQ = String(form.get('q') ?? '').trim()
          const nextActive = String(form.get('active') ?? '')
          if (nextQ) next.set('q', nextQ)
          else next.delete('q')
          if (nextActive === 'true' || nextActive === 'false') next.set('active', nextActive)
          else next.delete('active')
          next.set('page', '0')
          setSearchParams(next)
        }}
      >
        <label className="sr-only" htmlFor="admin-product-q">
          Search
        </label>
        <input
          id="admin-product-q"
          name="q"
          defaultValue={q}
          placeholder="Search name or SKU"
          className="min-w-[12rem] flex-1 rounded-lg border border-border bg-background px-3 py-2 text-sm"
        />
        <label className="sr-only" htmlFor="admin-product-active">
          Status filter
        </label>
        <select
          id="admin-product-active"
          name="active"
          defaultValue={activeParam ?? ''}
          className="rounded-lg border border-border bg-background px-3 py-2 text-sm"
        >
          <option value="">All statuses</option>
          <option value="true">Active</option>
          <option value="false">Inactive</option>
        </select>
        <button
          type="submit"
          className="rounded-lg border border-border px-3 py-2 text-sm font-medium hover:bg-muted"
        >
          Apply
        </button>
      </form>

      {statusError ? (
        <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
          {statusError}
        </p>
      ) : null}

      {productsQuery.isLoading ? (
        <p className="text-sm text-muted-foreground" role="status">
          Loading products…
        </p>
      ) : productsQuery.isError ? (
        <div className="space-y-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          <p role="alert">
            {productsQuery.error instanceof ApiClientError
              ? productsQuery.error.userMessage
              : 'Unable to load products.'}
          </p>
          <button
            type="button"
            className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground"
            onClick={() => void productsQuery.refetch()}
          >
            Retry
          </button>
        </div>
      ) : data && data.items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-card px-6 py-10 text-center">
          <p className="text-sm text-muted-foreground">No products match these filters.</p>
          <Link
            to="/admin/products/new"
            className="mt-4 inline-flex text-sm font-medium text-primary underline-offset-4 hover:underline"
          >
            Create a product
          </Link>
        </div>
      ) : data ? (
        <>
          <p className="text-sm text-muted-foreground">
            Showing page {data.page + 1} of {Math.max(data.totalPages, 1)} ({data.totalElements}{' '}
            total)
            {categoriesQuery.isSuccess ? ` · ${categoriesQuery.data.length} categories loaded` : ''}
          </p>
          <div className="overflow-x-auto rounded-2xl border border-border bg-card">
            <table className="w-full min-w-[40rem] text-left text-sm">
              <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">Name</th>
                  <th className="px-4 py-3 font-medium">SKU</th>
                  <th className="px-4 py-3 font-medium">Price</th>
                  <th className="px-4 py-3 font-medium">Category</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.items.map((product) => {
                  const pending =
                    statusMutation.isPending && statusMutation.variables?.id === product.id
                  return (
                    <tr key={product.id}>
                      <td className="px-4 py-3 font-medium text-foreground">{product.name}</td>
                      <td className="px-4 py-3 font-mono text-xs">{product.sku}</td>
                      <td className="px-4 py-3">
                        {formatProductPrice(product.unitPrice, product.currency)}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{product.categoryName}</td>
                      <td className="px-4 py-3">
                        <span
                          className={
                            product.active
                              ? 'font-medium text-emerald-800'
                              : 'font-medium text-amber-800'
                          }
                        >
                          {product.active ? 'Active' : 'Inactive'}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex flex-wrap gap-2">
                          <Link
                            to={`/admin/products/${product.id}/edit`}
                            className="font-medium text-primary underline-offset-4 hover:underline"
                          >
                            Edit
                          </Link>
                          <button
                            type="button"
                            disabled={pending}
                            className="font-medium text-foreground underline-offset-4 hover:underline disabled:opacity-60"
                            onClick={() => {
                              const next = !product.active
                              const ok = window.confirm(
                                next
                                  ? `Activate “${product.name}”?`
                                  : `Deactivate “${product.name}”? Soft-disable only — the product is not deleted.`,
                              )
                              if (!ok) return
                              setStatusError(null)
                              statusMutation.mutate({ id: product.id, active: next })
                            }}
                          >
                            {pending
                              ? 'Updating…'
                              : product.active
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
          <div className="flex gap-2">
            <button
              type="button"
              disabled={data.first}
              className="rounded-lg border border-border px-3 py-1.5 text-sm font-medium disabled:opacity-50"
              onClick={() => {
                const next = new URLSearchParams(searchParams)
                next.set('page', String(Math.max(0, page - 1)))
                setSearchParams(next)
              }}
            >
              Previous
            </button>
            <button
              type="button"
              disabled={data.last}
              className="rounded-lg border border-border px-3 py-1.5 text-sm font-medium disabled:opacity-50"
              onClick={() => {
                const next = new URLSearchParams(searchParams)
                next.set('page', String(page + 1))
                setSearchParams(next)
              }}
            >
              Next
            </button>
          </div>
        </>
      ) : null}
    </section>
  )
}
