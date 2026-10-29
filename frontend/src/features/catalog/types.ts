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

export const PRODUCT_DEFAULT_PAGE_SIZE = 20
export const PRODUCT_MAX_PAGE_SIZE = 100
export const PRODUCT_DEFAULT_SORT = 'name,asc'

export const PRODUCT_SORT_OPTIONS: { value: string; label: string }[] = [
  { value: 'name,asc', label: 'Name (A–Z)' },
  { value: 'name,desc', label: 'Name (Z–A)' },
  { value: 'price,asc', label: 'Price (low → high)' },
  { value: 'price,desc', label: 'Price (high → low)' },
  { value: 'createdAt,desc', label: 'Newest' },
  { value: 'createdAt,asc', label: 'Oldest' },
]

export function isAllowedProductSort(sort: string): boolean {
  return PRODUCT_SORT_OPTIONS.some((option) => option.value === sort)
}
