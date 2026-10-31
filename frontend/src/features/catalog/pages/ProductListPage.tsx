import { useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { ApiClientError } from '@/lib/api/api-error'

import { listCategories, listProducts, normalizeListParams } from '../api/catalog-api'
import { CatalogFilters } from '../components/CatalogFilters'
import { CatalogPagination } from '../components/CatalogPagination'
import { ProductGrid, ProductGridSkeleton } from '../components/ProductGrid'
import { catalogQueryKeys } from '../hooks/catalog-query-keys'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import {
  catalogStateToListParams,
  catalogStateToSearchParams,
  EMPTY_CATALOG_SEARCH,
  parseCatalogSearchParams,
  validatePriceFilters,
  type CatalogSearchState,
} from '../lib/catalog-search-params'

export function ProductListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const urlState = useMemo(() => parseCatalogSearchParams(searchParams), [searchParams])

  // null = follow URL; string = local typing draft (avoids syncing URL→state in an effect)
  const [draftQ, setDraftQ] = useState<string | null>(null)
  const qInput = draftQ ?? urlState.q
  const debouncedQ = useDebouncedValue(qInput, 300)

  useEffect(() => {
    // Only push debounced text while the user is typing (draftQ set).
    // Avoid re-applying a stale debounced q after Clear filters / non-q updates.
    if (draftQ === null || draftQ !== debouncedQ) {
      return
    }
    if (debouncedQ === urlState.q) {
      return
    }
    setSearchParams(
      catalogStateToSearchParams({ ...urlState, q: debouncedQ, page: 0 }),
      { replace: true },
    )
  }, [debouncedQ, draftQ, urlState, setSearchParams])

  const filterState: CatalogSearchState = { ...urlState, q: qInput }
  const queryState: CatalogSearchState = { ...urlState, q: debouncedQ }
  const priceError = validatePriceFilters(queryState.minPrice, queryState.maxPrice)
  const listParams = normalizeListParams(catalogStateToListParams(queryState))
  const filtersValid = priceError === null

  const writeState = (next: CatalogSearchState) => {
    setDraftQ(null)
    setSearchParams(catalogStateToSearchParams(next), { replace: true })
  }

  const applyState = (next: CatalogSearchState, options?: { resetPage?: boolean }) => {
    const page = options?.resetPage ? 0 : next.page
    if (next.q !== urlState.q) {
      setDraftQ(next.q)
      setSearchParams(
        catalogStateToSearchParams({ ...next, q: urlState.q, page }),
        { replace: true },
      )
      return
    }
    writeState({ ...next, page })
  }

  const clearFilters = () => {
    writeState({ ...EMPTY_CATALOG_SEARCH })
  }

  const categoriesQuery = useQuery({
    queryKey: catalogQueryKeys.categories(),
    queryFn: ({ signal }) => listCategories(signal),
    staleTime: 60_000,
  })

  const productsQuery = useQuery({
    queryKey: catalogQueryKeys.productList(listParams),
    queryFn: ({ signal }) => listProducts(listParams, signal),
    enabled: filtersValid,
    placeholderData: (previous) => previous,
    retry: false,
  })

  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <p className="text-sm font-medium text-muted-foreground">
          <Link to="/" className="text-primary hover:underline">
            Home
          </Link>
          <span aria-hidden="true"> / </span>
          Catalog
        </p>
        <h1 className="text-3xl font-semibold tracking-tight text-foreground">Products</h1>
        <p className="max-w-2xl text-sm text-muted-foreground">
          Browse active products from the catalog. Prices and availability come from the server.
        </p>
      </div>

      <CatalogFilters
        value={filterState}
        categories={categoriesQuery.data ?? []}
        priceError={priceError}
        onChange={applyState}
        onClear={clearFilters}
      />

      {!filtersValid ? (
        <p className="rounded-lg border border-destructive/20 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Fix the price filters to load products.
        </p>
      ) : productsQuery.isLoading ? (
        <div role="status" aria-live="polite" className="space-y-3">
          <p className="sr-only">Loading products…</p>
          <ProductGridSkeleton />
        </div>
      ) : productsQuery.isError ? (
        <div className="space-y-3 rounded-xl border border-destructive/20 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          <p role="alert">
            {productsQuery.error instanceof ApiClientError
              ? productsQuery.error.userMessage
              : 'Unable to load products.'}
          </p>
          <Button type="button" size="sm" onClick={() => void productsQuery.refetch()}>
            Retry
          </Button>
        </div>
      ) : (productsQuery.data?.items.length ?? 0) === 0 ? (
        <div className="rounded-xl border border-dashed border-border bg-card px-6 py-12 text-center">
          <p className="font-medium text-foreground">No products match these filters</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Try clearing filters or searching a different keyword.
          </p>
          <Button type="button" variant="link" className="mt-4" onClick={clearFilters}>
            Clear filters
          </Button>
        </div>
      ) : (
        <>
          {productsQuery.isFetching ? (
            <p className="text-xs text-muted-foreground" role="status">
              Updating…
            </p>
          ) : null}
          <ProductGrid products={productsQuery.data?.items ?? []} />
          <CatalogPagination
            page={productsQuery.data?.page ?? 0}
            totalPages={productsQuery.data?.totalPages ?? 0}
            totalElements={productsQuery.data?.totalElements ?? 0}
            disabled={productsQuery.isFetching}
            onPageChange={(page) => writeState({ ...filterState, q: debouncedQ, page })}
          />
        </>
      )}
    </section>
  )
}
