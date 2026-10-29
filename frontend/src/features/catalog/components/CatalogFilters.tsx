import type { Category } from '../types'
import { PRODUCT_SORT_OPTIONS } from '../types'
import type { CatalogSearchState } from '../lib/catalog-search-params'

type CatalogFiltersProps = {
  value: CatalogSearchState
  categories: Category[]
  priceError: string | null
  onChange: (next: CatalogSearchState, options?: { resetPage?: boolean }) => void
  onClear: () => void
}

export function CatalogFilters({
  value,
  categories,
  priceError,
  onChange,
  onClear,
}: CatalogFiltersProps) {
  const patch = (partial: Partial<CatalogSearchState>) => {
    onChange({ ...value, ...partial }, { resetPage: true })
  }

  const hasActiveFilters =
    Boolean(value.q.trim()) ||
    Boolean(value.categoryId) ||
    Boolean(value.minPrice) ||
    Boolean(value.maxPrice) ||
    value.inStock !== '' ||
    value.sort !== 'name,asc'

  return (
    <section
      className="space-y-4 rounded-2xl border border-border bg-card p-4 shadow-sm"
      aria-label="Catalog filters"
    >
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 className="text-sm font-semibold text-card-foreground">Filters</h2>
        {hasActiveFilters ? (
          <button
            type="button"
            className="text-sm font-medium text-primary underline-offset-4 hover:underline"
            onClick={onClear}
          >
            Clear filters
          </button>
        ) : null}
      </div>

      <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-foreground">Search</span>
          <input
            type="search"
            value={value.q}
            onChange={(event) => patch({ q: event.target.value })}
            placeholder="Name or description"
            className="w-full rounded-lg border border-border bg-background px-3 py-2"
          />
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-foreground">Category</span>
          <select
            value={value.categoryId}
            onChange={(event) => patch({ categoryId: event.target.value })}
            className="w-full rounded-lg border border-border bg-background px-3 py-2"
          >
            <option value="">All categories</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-foreground">Sort</span>
          <select
            value={value.sort}
            onChange={(event) => patch({ sort: event.target.value })}
            className="w-full rounded-lg border border-border bg-background px-3 py-2"
          >
            {PRODUCT_SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-foreground">Min price (VND)</span>
          <input
            type="number"
            min={0}
            inputMode="numeric"
            value={value.minPrice}
            onChange={(event) => patch({ minPrice: event.target.value })}
            className="w-full rounded-lg border border-border bg-background px-3 py-2"
          />
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-foreground">Max price (VND)</span>
          <input
            type="number"
            min={0}
            inputMode="numeric"
            value={value.maxPrice}
            onChange={(event) => patch({ maxPrice: event.target.value })}
            className="w-full rounded-lg border border-border bg-background px-3 py-2"
          />
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-foreground">Availability</span>
          <select
            value={value.inStock}
            onChange={(event) =>
              patch({ inStock: event.target.value as CatalogSearchState['inStock'] })
            }
            className="w-full rounded-lg border border-border bg-background px-3 py-2"
          >
            <option value="">Any</option>
            <option value="true">In stock</option>
            <option value="false">Out of stock</option>
          </select>
        </label>
      </div>

      {priceError ? (
        <p className="text-sm text-red-600" role="alert">
          {priceError}
        </p>
      ) : null}
    </section>
  )
}
