/** Matches backend `ProductResponse`. */
export type Product = {
  id: string
  sku: string
  name: string
  description: string | null
  unitPrice: string | number
  currency: string
  categoryId: string
  categoryName: string
  active: boolean
  imageUrl: string | null
  inStock: boolean | null
}

/** Matches backend `CategoryResponse` (public list returns active only). */
export type Category = {
  id: string
  name: string
  slug: string
  description: string | null
  parentId: string | null
  active: boolean
}

/** Matches backend `PageResponse<T>`. */
export type PageResponse<T> = {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type ProductSortField = 'name' | 'price' | 'createdAt'
export type SortDirection = 'asc' | 'desc'

export type ProductListParams = {
  q?: string
  categoryId?: string
  minPrice?: string
  maxPrice?: string
  inStock?: boolean
  page?: number
  size?: number
  sort?: string
}

export const PRODUCT_DEFAULT_PAGE_SIZE = 10
export const PRODUCT_MAX_PAGE_SIZE = 100
export const PRODUCT_DEFAULT_SORT = 'name,asc'

/** i18n key inside the `catalog` namespace (`catalog:sort.*`). */
export type ProductSortLabelKey =
  | 'sort.nameAsc'
  | 'sort.nameDesc'
  | 'sort.priceAsc'
  | 'sort.priceDesc'
  | 'sort.newest'
  | 'sort.oldest'

export const PRODUCT_SORT_OPTIONS: { value: string; labelKey: ProductSortLabelKey }[] = [
  { value: 'name,asc', labelKey: 'sort.nameAsc' },
  { value: 'name,desc', labelKey: 'sort.nameDesc' },
  { value: 'price,asc', labelKey: 'sort.priceAsc' },
  { value: 'price,desc', labelKey: 'sort.priceDesc' },
  { value: 'createdAt,desc', labelKey: 'sort.newest' },
  { value: 'createdAt,asc', labelKey: 'sort.oldest' },
]

export function isAllowedProductSort(sort: string): boolean {
  return PRODUCT_SORT_OPTIONS.some((option) => option.value === sort)
}
