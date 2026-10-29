import { apiRequest } from '@/lib/api/api-client'
import type { Category } from '@/features/catalog/types'

/** Matches backend CategoryCreateRequest / CategoryUpdateRequest. */
export type AdminCategoryWritePayload = {
  name: string
  description?: string | null
  parentId?: string | null
}

export type AdminCategoryStatusPayload = {
  active: boolean
}

/** GET /api/v1/admin/categories — includes inactive; no pagination. */
export function listAdminCategories(signal?: AbortSignal): Promise<Category[]> {
  return apiRequest<Category[]>('/admin/categories', { method: 'GET', auth: true, signal })
}

/** POST /api/v1/admin/categories */
export function createAdminCategory(
  payload: AdminCategoryWritePayload,
  signal?: AbortSignal,
): Promise<Category> {
  return apiRequest<Category>('/admin/categories', {
    method: 'POST',
    auth: true,
    body: {
      name: payload.name,
      description: payload.description ?? null,
      parentId: payload.parentId ?? null,
    },
    signal,
  })
}

/** PUT /api/v1/admin/categories/{id} */
export function updateAdminCategory(
  id: string,
  payload: AdminCategoryWritePayload,
  signal?: AbortSignal,
): Promise<Category> {
  return apiRequest<Category>(`/admin/categories/${encodeURIComponent(id)}`, {
    method: 'PUT',
    auth: true,
    body: {
      name: payload.name,
      description: payload.description ?? null,
      parentId: payload.parentId ?? null,
    },
    signal,
  })
}

/** PATCH /api/v1/admin/categories/{id}/status — soft-disable only */
export function updateAdminCategoryStatus(
  id: string,
  payload: AdminCategoryStatusPayload,
  signal?: AbortSignal,
): Promise<Category> {
  return apiRequest<Category>(`/admin/categories/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    auth: true,
    body: { active: payload.active },
    signal,
  })
}
