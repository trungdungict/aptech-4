import { apiRequest } from '@/lib/api/api-client'
import { toQueryString } from '@/lib/api/query-string'

import {
  isAllowedProductSort,
  PRODUCT_DEFAULT_PAGE_SIZE,
  PRODUCT_DEFAULT_SORT,
  PRODUCT_MAX_PAGE_SIZE,
  type Category,
  type PageResponse,
  type Product,
  type ProductListParams,
} from '../types'

function normalizeListParams(params: ProductListParams): ProductListParams {
  const page = params.page ?? 0
  const size = params.size ?? PRODUCT_DEFAULT_PAGE_SIZE
  const sort =
    params.sort && isAllowedProductSort(params.sort) ? params.sort : PRODUCT_DEFAULT_SORT

  return {
    q: params.q?.trim() || undefined,
    categoryId: params.categoryId || undefined,
    minPrice: params.minPrice || undefined,
    maxPrice: params.maxPrice || undefined,
    inStock: params.inStock,
    page: page < 0 ? 0 : page,
    size: Math.min(Math.max(size, 1), PRODUCT_MAX_PAGE_SIZE),
    sort,
  }
}

/** GET /api/v1/products — public active products in active categories. */
export async function listProducts(
  params: ProductListParams = {},
  signal?: AbortSignal,
): Promise<PageResponse<Product>> {
  const normalized = normalizeListParams(params)
  const query = toQueryString({
    q: normalized.q,
    categoryId: normalized.categoryId,
    minPrice: normalized.minPrice,
    maxPrice: normalized.maxPrice,
    inStock: normalized.inStock,
    page: normalized.page,
    size: normalized.size,
    sort: normalized.sort,
  })
  return apiRequest<PageResponse<Product>>(`/products${query}`, { method: 'GET', signal })
}

/** GET /api/v1/products/{id} — 404 if missing, inactive, or category inactive. */
export async function getProduct(id: string, signal?: AbortSignal): Promise<Product> {
  return apiRequest<Product>(`/products/${encodeURIComponent(id)}`, { method: 'GET', signal })
}

/** GET /api/v1/categories — public active categories only. */
export async function listCategories(signal?: AbortSignal): Promise<Category[]> {
  return apiRequest<Category[]>('/categories', { method: 'GET', signal })
}

export { normalizeListParams }
