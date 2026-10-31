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

/** Matches backend ProductCreateRequest / ProductUpdateRequest — no inventory. */
export type AdminProductWritePayload = {
  sku: string
  name: string
  description?: string | null
  unitPrice: number
  currency?: string
  categoryId: string
  /** Public relative path from upload API, or null to clear. */
  imageUrl?: string | null
}

export type AdminProductImageUploadResult = {
  imageUrl: string
  contentType: string
  sizeBytes: number
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
      imageUrl: payload.imageUrl ?? null,
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
      imageUrl: payload.imageUrl ?? null,
    },
    signal,
  })
}

/** POST /api/v1/admin/uploads/images — multipart field `file` */
export function uploadAdminProductImage(
  file: File,
  signal?: AbortSignal,
): Promise<AdminProductImageUploadResult> {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<AdminProductImageUploadResult>('/admin/uploads/images', {
    method: 'POST',
    auth: true,
    multipart: true,
    body,
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

/** Matches AdminProductInventoryResponse — per-product inventory row (ADR-006). */
export type AdminProductInventory = {
  productId: string
  quantityOnHand: number
  quantityReserved: number
  available: number
  updatedAt: string | null
}

export type AdminProductInventoryUpdatePayload = {
  quantityOnHand: number
}

/** GET /api/v1/admin/products/{id}/inventory */
export function getAdminProductInventory(
  id: string,
  signal?: AbortSignal,
): Promise<AdminProductInventory> {
  return apiRequest<AdminProductInventory>(
    `/admin/products/${encodeURIComponent(id)}/inventory`,
    {
      method: 'GET',
      auth: true,
      signal,
    },
  )
}

/** PUT /api/v1/admin/products/{id}/inventory — absolute quantity_on_hand */
export function updateAdminProductInventory(
  id: string,
  payload: AdminProductInventoryUpdatePayload,
  signal?: AbortSignal,
): Promise<AdminProductInventory> {
  return apiRequest<AdminProductInventory>(
    `/admin/products/${encodeURIComponent(id)}/inventory`,
    {
      method: 'PUT',
      auth: true,
      body: { quantityOnHand: payload.quantityOnHand },
      signal,
    },
  )
}
