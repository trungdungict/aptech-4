import { FilterIcon } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { useMediaQuery } from '@/hooks/use-media-query'
import { cn } from '@/lib/utils'

import type { CatalogSearchState } from '../lib/catalog-search-params'
import type { Category } from '../types'
import { PRODUCT_SORT_OPTIONS } from '../types'

type CatalogFiltersProps = {
  value: CatalogSearchState
  categories: Category[]
  priceError: string | null
  onChange: (next: CatalogSearchState, options?: { resetPage?: boolean }) => void
  onClear: () => void
}

const selectClassName = cn(
  'flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs outline-none',
  'focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50',
  'disabled:cursor-not-allowed disabled:opacity-50',
)

function FilterFields({
  value,
  categories,
  priceError,
  onChange,
  onClear,
  hasActiveFilters,
}: CatalogFiltersProps & { hasActiveFilters: boolean }) {
  const patch = (partial: Partial<CatalogSearchState>) => {
    onChange({ ...value, ...partial }, { resetPage: true })
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 className="text-sm font-semibold text-card-foreground">Filters</h2>
        {hasActiveFilters ? (
          <Button type="button" variant="link" className="h-auto p-0" onClick={onClear}>
            Clear filters
          </Button>
        ) : null}
      </div>

      <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
        <div className="space-y-1.5">
          <Label htmlFor="catalog-search">Search</Label>
          <Input
            id="catalog-search"
            type="search"
            value={value.q}
            onChange={(event) => patch({ q: event.target.value })}
            placeholder="Name or description"
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="catalog-category">Category</Label>
          <select
            id="catalog-category"
            value={value.categoryId}
            onChange={(event) => patch({ categoryId: event.target.value })}
            className={selectClassName}
          >
            <option value="">All categories</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="catalog-sort">Sort</Label>
          <select
            id="catalog-sort"
            value={value.sort}
            onChange={(event) => patch({ sort: event.target.value })}
            className={selectClassName}
          >
            {PRODUCT_SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="catalog-min-price">Min price (VND)</Label>
          <Input
            id="catalog-min-price"
            type="number"
            min={0}
            inputMode="numeric"
            value={value.minPrice}
            onChange={(event) => patch({ minPrice: event.target.value })}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="catalog-max-price">Max price (VND)</Label>
          <Input
            id="catalog-max-price"
            type="number"
            min={0}
            inputMode="numeric"
            value={value.maxPrice}
            onChange={(event) => patch({ maxPrice: event.target.value })}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="catalog-availability">Availability</Label>
          <select
            id="catalog-availability"
            value={value.inStock}
            onChange={(event) =>
              patch({ inStock: event.target.value as CatalogSearchState['inStock'] })
            }
            className={selectClassName}
          >
            <option value="">Any</option>
            <option value="true">In stock</option>
            <option value="false">Out of stock</option>
          </select>
        </div>
      </div>

      {priceError ? (
        <p className="text-sm text-destructive" role="alert">
          {priceError}
        </p>
      ) : null}
    </div>
  )
}

export function CatalogFilters(props: CatalogFiltersProps) {
  const isDesktop = useMediaQuery('(min-width: 768px)', true)
  const [mobileOpen, setMobileOpen] = useState(false)

  const hasActiveFilters =
    Boolean(props.value.q.trim()) ||
    Boolean(props.value.categoryId) ||
    Boolean(props.value.minPrice) ||
    Boolean(props.value.maxPrice) ||
    props.value.inStock !== '' ||
    props.value.sort !== 'name,asc'

  if (isDesktop) {
    return (
      <section
        className="space-y-4 rounded-xl border border-border bg-card p-4 shadow-sm"
        aria-label="Catalog filters"
      >
        <FilterFields {...props} hasActiveFilters={hasActiveFilters} />
      </section>
    )
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <Button
        type="button"
        variant="outline"
        size="sm"
        aria-label="Open catalog filters"
        onClick={() => setMobileOpen(true)}
      >
        <FilterIcon className="size-4" />
        Filters
        {hasActiveFilters ? (
          <span className="rounded-full bg-primary px-1.5 text-[10px] font-semibold text-primary-foreground">
            On
          </span>
        ) : null}
      </Button>
      {hasActiveFilters ? (
        <Button type="button" variant="link" className="h-auto p-0" onClick={props.onClear}>
          Clear filters
        </Button>
      ) : null}

      <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetContent side="bottom" className="max-h-[85vh] overflow-y-auto rounded-t-xl">
          <SheetHeader>
            <SheetTitle>Catalog filters</SheetTitle>
            <SheetDescription>Search, category, price, and sort</SheetDescription>
          </SheetHeader>
          <div className="px-4 pb-6" aria-label="Catalog filters">
            <FilterFields {...props} hasActiveFilters={hasActiveFilters} />
            <Button type="button" className="mt-4 w-full" onClick={() => setMobileOpen(false)}>
              Show results
            </Button>
          </div>
        </SheetContent>
      </Sheet>
    </div>
  )
}
