import { apiRequest } from '@/lib/api/api-client'

import type {
  AdminCampaignPreview,
  AdminImageUploadResult,
  AdminPromotionCampaign,
  AdminPromotionCampaignWrite,
  AdminPromotionEligibilityWrite,
  AdminPromotionPlacement,
  AdminPromotionPlacementWrite,
  AdminPromotionStrategy,
  AdminPromotionStrategyWrite,
} from '../types'

/** GET /api/v1/admin/promotions/strategies */
export function listAdminStrategies(signal?: AbortSignal): Promise<AdminPromotionStrategy[]> {
  return apiRequest<AdminPromotionStrategy[]>('/admin/promotions/strategies', {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** GET /api/v1/admin/promotions/strategies/{id} */
export function getAdminStrategy(id: string, signal?: AbortSignal): Promise<AdminPromotionStrategy> {
  return apiRequest<AdminPromotionStrategy>(`/admin/promotions/strategies/${encodeURIComponent(id)}`, {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** POST /api/v1/admin/promotions/strategies */
export function createAdminStrategy(
  payload: AdminPromotionStrategyWrite,
  signal?: AbortSignal,
): Promise<AdminPromotionStrategy> {
  return apiRequest<AdminPromotionStrategy>('/admin/promotions/strategies', {
    method: 'POST',
    auth: true,
    body: payload,
    signal,
  })
}

/** PUT /api/v1/admin/promotions/strategies/{id} */
export function updateAdminStrategy(
  id: string,
  payload: AdminPromotionStrategyWrite,
  signal?: AbortSignal,
): Promise<AdminPromotionStrategy> {
  return apiRequest<AdminPromotionStrategy>(`/admin/promotions/strategies/${encodeURIComponent(id)}`, {
    method: 'PUT',
    auth: true,
    body: payload,
    signal,
  })
}

/** PATCH /api/v1/admin/promotions/strategies/{id}/status */
export function updateAdminStrategyStatus(
  id: string,
  active: boolean,
  signal?: AbortSignal,
): Promise<AdminPromotionStrategy> {
  return apiRequest<AdminPromotionStrategy>(
    `/admin/promotions/strategies/${encodeURIComponent(id)}/status`,
    {
      method: 'PATCH',
      auth: true,
      body: { active },
      signal,
    },
  )
}

/** GET /api/v1/admin/promotions/campaigns */
export function listAdminCampaigns(signal?: AbortSignal): Promise<AdminPromotionCampaign[]> {
  return apiRequest<AdminPromotionCampaign[]>('/admin/promotions/campaigns', {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** GET /api/v1/admin/promotions/campaigns/{id} */
export function getAdminCampaign(id: string, signal?: AbortSignal): Promise<AdminPromotionCampaign> {
  return apiRequest<AdminPromotionCampaign>(`/admin/promotions/campaigns/${encodeURIComponent(id)}`, {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** POST /api/v1/admin/promotions/campaigns */
export function createAdminCampaign(
  payload: AdminPromotionCampaignWrite,
  signal?: AbortSignal,
): Promise<AdminPromotionCampaign> {
  return apiRequest<AdminPromotionCampaign>('/admin/promotions/campaigns', {
    method: 'POST',
    auth: true,
    body: payload,
    signal,
  })
}

/** PUT /api/v1/admin/promotions/campaigns/{id} */
export function updateAdminCampaign(
  id: string,
  payload: AdminPromotionCampaignWrite,
  signal?: AbortSignal,
): Promise<AdminPromotionCampaign> {
  return apiRequest<AdminPromotionCampaign>(`/admin/promotions/campaigns/${encodeURIComponent(id)}`, {
    method: 'PUT',
    auth: true,
    body: payload,
    signal,
  })
}

/** PUT /api/v1/admin/promotions/campaigns/{id}/eligibility */
export function replaceAdminCampaignEligibility(
  id: string,
  payload: AdminPromotionEligibilityWrite,
  signal?: AbortSignal,
): Promise<AdminPromotionCampaign> {
  return apiRequest<AdminPromotionCampaign>(
    `/admin/promotions/campaigns/${encodeURIComponent(id)}/eligibility`,
    {
      method: 'PUT',
      auth: true,
      body: payload,
      signal,
    },
  )
}

/** POST …/activate | pause | schedule */
export function activateAdminCampaign(id: string, signal?: AbortSignal): Promise<AdminPromotionCampaign> {
  return apiRequest<AdminPromotionCampaign>(
    `/admin/promotions/campaigns/${encodeURIComponent(id)}/activate`,
    { method: 'POST', auth: true, signal },
  )
}

export function pauseAdminCampaign(id: string, signal?: AbortSignal): Promise<AdminPromotionCampaign> {
  return apiRequest<AdminPromotionCampaign>(
    `/admin/promotions/campaigns/${encodeURIComponent(id)}/pause`,
    { method: 'POST', auth: true, signal },
  )
}

export function scheduleAdminCampaign(id: string, signal?: AbortSignal): Promise<AdminPromotionCampaign> {
  return apiRequest<AdminPromotionCampaign>(
    `/admin/promotions/campaigns/${encodeURIComponent(id)}/schedule`,
    { method: 'POST', auth: true, signal },
  )
}

/** POST …/preview — schedule effectiveness only (pricing is P22). */
export function previewAdminCampaign(
  id: string,
  at?: string | null,
  signal?: AbortSignal,
): Promise<AdminCampaignPreview> {
  return apiRequest<AdminCampaignPreview>(
    `/admin/promotions/campaigns/${encodeURIComponent(id)}/preview`,
    {
      method: 'POST',
      auth: true,
      body: at ? { at } : {},
      signal,
    },
  )
}

/** DELETE /api/v1/admin/promotions/campaigns/{id} */
export function deleteAdminCampaign(id: string, signal?: AbortSignal): Promise<void> {
  return apiRequest<void>(`/admin/promotions/campaigns/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    auth: true,
    signal,
  })
}

/** Placement CRUD */
export function listAdminCampaignPlacements(
  campaignId: string,
  signal?: AbortSignal,
): Promise<AdminPromotionPlacement[]> {
  return apiRequest<AdminPromotionPlacement[]>(
    `/admin/promotions/campaigns/${encodeURIComponent(campaignId)}/placements`,
    { method: 'GET', auth: true, signal },
  )
}

export function createAdminPlacement(
  campaignId: string,
  payload: AdminPromotionPlacementWrite,
  signal?: AbortSignal,
): Promise<AdminPromotionPlacement> {
  return apiRequest<AdminPromotionPlacement>(
    `/admin/promotions/campaigns/${encodeURIComponent(campaignId)}/placements`,
    { method: 'POST', auth: true, body: payload, signal },
  )
}

export function updateAdminPlacement(
  id: string,
  payload: AdminPromotionPlacementWrite,
  signal?: AbortSignal,
): Promise<AdminPromotionPlacement> {
  return apiRequest<AdminPromotionPlacement>(`/admin/promotions/placements/${encodeURIComponent(id)}`, {
    method: 'PUT',
    auth: true,
    body: payload,
    signal,
  })
}

export function deleteAdminPlacement(id: string, signal?: AbortSignal): Promise<void> {
  return apiRequest<void>(`/admin/promotions/placements/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    auth: true,
    signal,
  })
}

/** POST /api/v1/admin/uploads/images?purpose=PROMOTION */
export function uploadAdminPromotionImage(
  file: File,
  signal?: AbortSignal,
): Promise<AdminImageUploadResult> {
  const body = new FormData()
  body.append('file', file)
  return apiRequest<AdminImageUploadResult>('/admin/uploads/images?purpose=PROMOTION', {
    method: 'POST',
    auth: true,
    multipart: true,
    body,
    signal,
  })
}
