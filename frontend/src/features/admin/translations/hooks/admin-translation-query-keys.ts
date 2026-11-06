import type { EntityTranslationKind, NameTranslationKind } from '../types'

/**
 * Translation editors load BOTH locales in one payload, so keys are intentionally locale-free.
 * After a successful PUT the locale-aware list/detail keys (products, categories, promotions,
 * catalog, public promotions) are invalidated by `invalidateAfterTranslationSave`.
 */
export const adminTranslationQueryKeys = {
  all: ['admin', 'translations'] as const,
  entity: (kind: EntityTranslationKind, id: string) =>
    [...adminTranslationQueryKeys.all, kind, id] as const,
  name: (kind: NameTranslationKind, id: string) =>
    [...adminTranslationQueryKeys.all, kind, id] as const,
  placement: (id: string) => [...adminTranslationQueryKeys.all, 'placement', id] as const,
}
