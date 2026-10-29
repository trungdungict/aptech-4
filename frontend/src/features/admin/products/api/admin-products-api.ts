import { apiRequest } from '@/lib/api/api-client'
import { toQueryString } from '@/lib/api/query-string'
import {
  isAllowedProductSort,
  PRODUCT_DEFAULT_PAGE_SIZE,
  PRODUCT_DEFAULT_SORT,
  PRODUCT_MAX_PAGE_SIZE,
  type PageResponse,
  type Product,
} from '@/features/catalog/types'

export type AdminProductListParams = {
  q?: string
  categoryId?: string
  active?: boolean
  page?: number
  size?: number
  sort?: string
}

/** Matches backend ProductCreateRequest / ProductUpdateRequest — no inventory or imageUrl. */
export type AdminProductWritePayload = {
  sku: string
  name: string
  description?: string | null
  unitPrice: number
  currency?: string
  categoryId: string
}

export type AdminProductStatusPayload = {
  active: boolean
}

function normalizeAdminListParams(params: AdminProductListParams): AdminProductListParams {
  const page = params.page ?? 0
  const size = params.size ?? PRODUCT_DEFAULT_PAGE_SIZE
  const sort =
    params.sort && isAllowedProductSort(params.sort) ? params.sort : PRODUCT_DEFAULT_SORT

  return {
    q: params.q?.trim() || undefined,
    categoryId: params.categoryId || undefined,
    active: params.active,
    page: page < 0 ? 0 : page,
    size: Math.min(Math.max(size, 1), PRODUCT_MAX_PAGE_SIZE),
    sort,
  }
}

/** GET /api/v1/admin/products */
export function listAdminProducts(
  params: AdminProductListParams = {},
  signal?: AbortSignal,
): Promise<PageResponse<Product>> {
  const normalized = normalizeAdminListParams(params)
  const query = toQueryString({
    q: normalized.q,
    categoryId: normalized.categoryId,
    active: normalized.active,
    page: normalized.page,
    size: normalized.size,
    sort: normalized.sort,
  })
  return apiRequest<PageResponse<Product>>(`/admin/products${query}`, {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** GET /api/v1/admin/products/{id} */
export function getAdminProduct(id: string, signal?: AbortSignal): Promise<Product> {
  return apiRequest<Product>(`/admin/products/${encodeURIComponent(id)}`, {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** POST /api/v1/admin/products */
export function createAdminProduct(
  payload: AdminProductWritePayload,
  signal?: AbortSignal,
): Promise<Product> {
  return apiRequest<Product>('/admin/products', {
    method: 'POST',
    auth: true,
    body: {
      sku: payload.sku,
      name: payload.name,
      description: payload.description ?? null,
      unitPrice: payload.unitPrice,
      currency: payload.currency || 'VND',
      categoryId: payload.categoryId,
    },
    signal,
  })
}

/** PUT /api/v1/admin/products/{id} */
export function updateAdminProduct(
  id: string,
  payload: AdminProductWritePayload,
  signal?: AbortSignal,
): Promise<Product> {
  return apiRequest<Product>(`/admin/products/${encodeURIComponent(id)}`, {
    method: 'PUT',
    auth: true,
    body: {
      sku: payload.sku,
      name: payload.name,
      description: payload.description ?? null,
      unitPrice: payload.unitPrice,
      currency: payload.currency || 'VND',
      categoryId: payload.categoryId,
    },
    signal,
  })
}

/** PATCH /api/v1/admin/products/{id}/status — soft-disable only */
export function updateAdminProductStatus(
  id: string,
  payload: AdminProductStatusPayload,
  signal?: AbortSignal,
): Promise<Product> {
  return apiRequest<Product>(`/admin/products/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    auth: true,
    body: { active: payload.active },
    signal,
  })
}
