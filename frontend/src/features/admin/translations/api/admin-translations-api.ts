import { apiRequest } from '@/lib/api/api-client'

import type {
  EntityTranslationKind,
  EntityTranslationsPayload,
  EntityTranslationsResponse,
  NameTranslationKind,
  NameTranslationsPayload,
  NameTranslationsResponse,
  PlacementTranslationsPayload,
  PlacementTranslationsResponse,
} from '../types'

/** Guards against a response that is not the T04 `{ id, translations }` contract. */
function assertTranslationsShape<T>(data: T): T {
  const translations = (data as { translations?: unknown } | null)?.translations
  if (typeof translations !== 'object' || translations === null) {
    throw new Error('Malformed translations response')
  }
  return data
}

async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  return assertTranslationsShape(await apiRequest<T>(path, { method: 'GET', auth: true, signal }))
}

async function put<T>(path: string, body: unknown, signal?: AbortSignal): Promise<T> {
  return assertTranslationsShape(
    await apiRequest<T>(path, { method: 'PUT', auth: true, body, signal }),
  )
}

function entityPath(kind: EntityTranslationKind, id: string): string {
  const segment = kind === 'product' ? 'products' : 'categories'
  return `/admin/${segment}/${encodeURIComponent(id)}/translations`
}

function namePath(kind: NameTranslationKind, id: string): string {
  const segment = kind === 'strategy' ? 'strategies' : 'campaigns'
  return `/admin/promotions/${segment}/${encodeURIComponent(id)}/translations`
}

function placementPath(id: string): string {
  return `/admin/promotions/placements/${encodeURIComponent(id)}/translations`
}

/** GET /api/v1/admin/{products|categories}/{id}/translations */
export function getEntityTranslations(
  kind: EntityTranslationKind,
  id: string,
  signal?: AbortSignal,
): Promise<EntityTranslationsResponse> {
  return get<EntityTranslationsResponse>(entityPath(kind, id), signal)
}

/** PUT /api/v1/admin/{products|categories}/{id}/translations */
export function putEntityTranslations(
  kind: EntityTranslationKind,
  id: string,
  payload: EntityTranslationsPayload,
  signal?: AbortSignal,
): Promise<EntityTranslationsResponse> {
  return put<EntityTranslationsResponse>(entityPath(kind, id), payload, signal)
}

/** GET /api/v1/admin/promotions/{strategies|campaigns}/{id}/translations */
export function getNameTranslations(
  kind: NameTranslationKind,
  id: string,
  signal?: AbortSignal,
): Promise<NameTranslationsResponse> {
  return get<NameTranslationsResponse>(namePath(kind, id), signal)
}

/** PUT /api/v1/admin/promotions/{strategies|campaigns}/{id}/translations */
export function putNameTranslations(
  kind: NameTranslationKind,
  id: string,
  payload: NameTranslationsPayload,
  signal?: AbortSignal,
): Promise<NameTranslationsResponse> {
  return put<NameTranslationsResponse>(namePath(kind, id), payload, signal)
}

/** GET /api/v1/admin/promotions/placements/{id}/translations */
export function getPlacementTranslations(
  id: string,
  signal?: AbortSignal,
): Promise<PlacementTranslationsResponse> {
  return get<PlacementTranslationsResponse>(placementPath(id), signal)
}

/** PUT /api/v1/admin/promotions/placements/{id}/translations */
export function putPlacementTranslations(
  id: string,
  payload: PlacementTranslationsPayload,
  signal?: AbortSignal,
): Promise<PlacementTranslationsResponse> {
  return put<PlacementTranslationsResponse>(placementPath(id), payload, signal)
}
