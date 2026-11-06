import { FilterIcon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

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

import type { CatalogSearchState, PriceFilterErrorKey } from '../lib/catalog-search-params'
import type { Category } from '../types'
import { PRODUCT_SORT_OPTIONS } from '../types'

type CatalogFiltersProps = {
  value: CatalogSearchState
  categories: Category[]
  priceError: PriceFilterErrorKey | null
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
  const { t } = useTranslation('catalog')
  const { t: tv } = useTranslation('validation')
  const patch = (partial: Partial<CatalogSearchState>) => {
    onChange({ ...value, ...partial }, { resetPage: true })
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 className="text-sm font-semibold text-card-foreground">{t('filters.title')}</h2>
        {hasActiveFilters ? (
          <Button type="button" variant="link" className="h-auto p-0" onClick={onClear}>
            {t('filters.clear')}
          </Button>
        ) : null}
      </div>

      <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
        <div className="space-y-1.5">
          <Label htmlFor="catalog-search">{t('filters.search')}</Label>
          <Input
            id="catalog-search"
            type="search"
            value={value.q}
            onChange={(event) => patch({ q: event.target.value })}
            placeholder={t('filters.searchPlaceholder')}
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="catalog-category">{t('filters.category')}</Label>
          <select
            id="catalog-category"
            value={value.categoryId}
            onChange={(event) => patch({ categoryId: event.target.value })}
            className={selectClassName}
          >
            <option value="">{t('filters.allCategories')}</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="catalog-sort">{t('filters.sort')}</Label>
          <select
            id="catalog-sort"
            value={value.sort}
            onChange={(event) => patch({ sort: event.target.value })}
            className={selectClassName}
          >
            {PRODUCT_SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {t(option.labelKey)}
              </option>
            ))}
          </select>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="catalog-min-price">{t('filters.minPrice')}</Label>
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
          <Label htmlFor="catalog-max-price">{t('filters.maxPrice')}</Label>
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
          <Label htmlFor="catalog-availability">{t('filters.availability')}</Label>
          <select
            id="catalog-availability"
            value={value.inStock}
            onChange={(event) =>
              patch({ inStock: event.target.value as CatalogSearchState['inStock'] })
            }
            className={selectClassName}
          >
            <option value="">{t('filters.any')}</option>
            <option value="true">{t('filters.inStock')}</option>
            <option value="false">{t('filters.outOfStock')}</option>
          </select>
        </div>
      </div>

      {priceError ? (
        <p className="text-sm text-destructive" role="alert">
          {tv(priceError)}
        </p>
      ) : null}
    </div>
  )
}

export function CatalogFilters(props: CatalogFiltersProps) {
  const { t } = useTranslation('catalog')
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
        aria-label={t('filters.ariaLabel')}
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
        aria-label={t('filters.openAria')}
        onClick={() => setMobileOpen(true)}
      >
        <FilterIcon className="size-4" />
        {t('filters.title')}
        {hasActiveFilters ? (
          <span className="rounded-full bg-primary px-1.5 text-[10px] font-semibold text-primary-foreground">
            {t('filters.activeBadge')}
          </span>
        ) : null}
      </Button>
      {hasActiveFilters ? (
        <Button type="button" variant="link" className="h-auto p-0" onClick={props.onClear}>
          {t('filters.clear')}
        </Button>
      ) : null}

      <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetContent side="bottom" className="max-h-[85vh] overflow-y-auto rounded-t-xl">
          <SheetHeader>
            <SheetTitle>{t('filters.sheetTitle')}</SheetTitle>
            <SheetDescription>{t('filters.sheetDescription')}</SheetDescription>
          </SheetHeader>
          <div className="px-4 pb-6" aria-label={t('filters.ariaLabel')}>
            <FilterFields {...props} hasActiveFilters={hasActiveFilters} />
            <Button type="button" className="mt-4 w-full" onClick={() => setMobileOpen(false)}>
              {t('filters.showResults')}
            </Button>
          </div>
        </SheetContent>
      </Sheet>
    </div>
  )
}
