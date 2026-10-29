import {
  isAllowedProductSort,
  PRODUCT_DEFAULT_PAGE_SIZE,
  PRODUCT_DEFAULT_SORT,
  type ProductListParams,
} from '../types'

export type CatalogSearchState = {
  q: string
  categoryId: string
  minPrice: string
  maxPrice: string
  inStock: '' | 'true' | 'false'
  sort: string
  page: number
  size: number
}

export const EMPTY_CATALOG_SEARCH: CatalogSearchState = {
  q: '',
  categoryId: '',
  minPrice: '',
  maxPrice: '',
  inStock: '',
  sort: PRODUCT_DEFAULT_SORT,
  page: 0,
  size: PRODUCT_DEFAULT_PAGE_SIZE,
}

export function parseCatalogSearchParams(params: URLSearchParams): CatalogSearchState {
  const sortRaw = params.get('sort') ?? PRODUCT_DEFAULT_SORT
  const pageRaw = Number(params.get('page') ?? '0')
  const sizeRaw = Number(params.get('size') ?? String(PRODUCT_DEFAULT_PAGE_SIZE))
  const inStockRaw = params.get('inStock')

  return {
    q: params.get('q') ?? '',
    categoryId: params.get('categoryId') ?? '',
    minPrice: params.get('minPrice') ?? '',
    maxPrice: params.get('maxPrice') ?? '',
    inStock: inStockRaw === 'true' || inStockRaw === 'false' ? inStockRaw : '',
    sort: isAllowedProductSort(sortRaw) ? sortRaw : PRODUCT_DEFAULT_SORT,
    page: Number.isFinite(pageRaw) && pageRaw >= 0 ? Math.floor(pageRaw) : 0,
    size:
      Number.isFinite(sizeRaw) && sizeRaw >= 1
        ? Math.min(Math.floor(sizeRaw), 100)
        : PRODUCT_DEFAULT_PAGE_SIZE,
  }
}

export function catalogStateToSearchParams(state: CatalogSearchState): URLSearchParams {
  const params = new URLSearchParams()
  if (state.q.trim()) params.set('q', state.q.trim())
  if (state.categoryId) params.set('categoryId', state.categoryId)
  if (state.minPrice) params.set('minPrice', state.minPrice)
  if (state.maxPrice) params.set('maxPrice', state.maxPrice)
  if (state.inStock === 'true' || state.inStock === 'false') {
    params.set('inStock', state.inStock)
  }
  if (state.sort && state.sort !== PRODUCT_DEFAULT_SORT) {
    params.set('sort', state.sort)
  }
  if (state.page > 0) params.set('page', String(state.page))
  if (state.size !== PRODUCT_DEFAULT_PAGE_SIZE) params.set('size', String(state.size))
  return params
}

export function catalogStateToListParams(state: CatalogSearchState): ProductListParams {
  return {
    q: state.q.trim() || undefined,
    categoryId: state.categoryId || undefined,
    minPrice: state.minPrice || undefined,
    maxPrice: state.maxPrice || undefined,
    inStock: state.inStock === '' ? undefined : state.inStock === 'true',
    page: state.page,
    size: state.size,
    sort: state.sort,
  }
}

export function validatePriceFilters(
  minPrice: string,
  maxPrice: string,
): string | null {
  const min = minPrice === '' ? null : Number(minPrice)
  const max = maxPrice === '' ? null : Number(maxPrice)
  if (min !== null && (!Number.isFinite(min) || min < 0)) {
    return 'Minimum price must be a number ≥ 0'
  }
  if (max !== null && (!Number.isFinite(max) || max < 0)) {
    return 'Maximum price must be a number ≥ 0'
  }
  if (min !== null && max !== null && min > max) {
    return 'Minimum price cannot exceed maximum price'
  }
  return null
}
