import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PackageIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { listAdminCategories } from '@/features/admin/categories/api/admin-categories-api'
import { adminCategoryQueryKeys } from '@/features/admin/categories/hooks/admin-category-query-keys'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { catalogQueryKeys } from '@/features/catalog/hooks/catalog-query-keys'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { PRODUCT_DEFAULT_PAGE_SIZE, PRODUCT_DEFAULT_SORT } from '@/features/catalog/types'
import { ApiClientError } from '@/lib/api/api-error'

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
    <section className={adminPageSectionClass}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-1">
          <h1 className={adminPageTitleClass}>
            Products
          </h1>
          <p className={adminPageLeadClass}>
            Admin list includes inactive products. Soft-disable via status — no hard delete.
            Inventory cannot be changed here.
          </p>
        </div>
        <Button asChild>
          <Link to="/admin/products/new">New product</Link>
        </Button>
      </div>

      <Card className="shadow-sm">
        <CardContent className="pt-6">
          <form
            className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end"
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
            <div className="min-w-[12rem] flex-1 space-y-1.5">
              <Label htmlFor="admin-product-q">Search</Label>
              <Input
                id="admin-product-q"
                name="q"
                defaultValue={q}
                placeholder="Search name or SKU"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="admin-product-active">Status filter</Label>
              <select
                id="admin-product-active"
                name="active"
                defaultValue={activeParam ?? ''}
                className={adminNativeSelectClass}
              >
                <option value="">All statuses</option>
                <option value="true">Active</option>
                <option value="false">Inactive</option>
              </select>
            </div>
            <Button type="submit" variant="outline">
              Apply
            </Button>
          </form>
        </CardContent>
      </Card>

      {statusError ? (
        <p
          className={adminAlertErrorClass}
          role="alert"
        >
          {statusError}
        </p>
      ) : null}

      {productsQuery.isLoading ? (
        <div className="space-y-3" role="status" aria-live="polite">
          <p className="sr-only">Loading products…</p>
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-48 w-full rounded-xl" />
        </div>
      ) : productsQuery.isError ? (
        <div className={`space-y-3 ${adminAlertErrorClass}`}>
          <p role="alert">
            {productsQuery.error instanceof ApiClientError
              ? productsQuery.error.userMessage
              : 'Unable to load products.'}
          </p>
          <Button type="button" size="sm" onClick={() => void productsQuery.refetch()}>
            Retry
          </Button>
        </div>
      ) : data && data.items.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <PackageIcon className="size-10 text-muted-foreground opacity-60" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">No products match these filters.</p>
            <Button asChild variant="link">
              <Link to="/admin/products/new">Create a product</Link>
            </Button>
          </CardContent>
        </Card>
      ) : data ? (
        <>
          <p className="text-sm text-muted-foreground">
            Showing page {data.page + 1} of {Math.max(data.totalPages, 1)} ({data.totalElements}{' '}
            total)
            {categoriesQuery.isSuccess ? ` · ${categoriesQuery.data.length} categories loaded` : ''}
          </p>
          <div className={adminTableWrapClass}>
            <table className={`${adminTableClass} min-w-[40rem]`}>
              <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                <tr>
                  <th className={adminThClass}>Name</th>
                  <th className={adminThClass}>SKU</th>
                  <th className={adminThClass}>Price</th>
                  <th className={adminThClass}>Category</th>
                  <th className={adminThClass}>Status</th>
                  <th className={adminThClass}>Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.items.map((product) => {
                  const pending =
                    statusMutation.isPending && statusMutation.variables?.id === product.id
                  return (
                    <tr key={product.id} className="hover:bg-muted/30">
                      <td className={`${adminTdClass} font-medium text-foreground`}>{product.name}</td>
                      <td className={`${adminTdClass} font-mono text-xs`}>{product.sku}</td>
                      <td className={adminTdClass}>
                        {formatProductPrice(product.unitPrice, product.currency)}
                      </td>
                      <td className={`${adminTdClass} text-muted-foreground`}>{product.categoryName}</td>
                      <td className={adminTdClass}>
                        <Badge variant={product.active ? 'success' : 'warning'}>
                          {product.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </td>
                      <td className={adminTdClass}>
                        <div className="flex flex-wrap gap-2">
                          <Button asChild variant="link" size="sm" className="h-auto px-0">
                            <Link to={`/admin/products/${product.id}/edit`}>Edit</Link>
                          </Button>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            disabled={pending}
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
                          </Button>
                        </div>
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
              disabled={data.first}
              onClick={() => {
                const next = new URLSearchParams(searchParams)
                next.set('page', String(Math.max(0, page - 1)))
                setSearchParams(next)
              }}
            >
              Previous
            </Button>
            <Button
              type="button"
              variant="outline"
              disabled={data.last}
              onClick={() => {
                const next = new URLSearchParams(searchParams)
                next.set('page', String(page + 1))
                setSearchParams(next)
              }}
            >
              Next
            </Button>
          </div>
        </>
      ) : null}
    </section>
  )
}
