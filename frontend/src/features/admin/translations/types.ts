/** Admin bilingual translation DTOs (P34-T04 frozen contract). Locale keys are `vi` | `en`. */

export type TranslationLocale = 'vi' | 'en'

/** Product / category editable text. Missing EN comes back as nulls. */
export type EntityLocaleText = {
  name: string | null
  description: string | null
}

export type EntityTranslationsResponse = {
  id: string
  translations: Partial<Record<TranslationLocale, EntityLocaleText>>
}

export type EntityTranslationsPayload = {
  translations: {
    vi: { name: string; description: string | null }
    en?: { name: string; description: string | null }
  }
}

/** Strategy / campaign name-only translations. */
export type NameTranslationsResponse = {
  id: string
  translations: Partial<Record<TranslationLocale, string | null>>
}

export type NameTranslationsPayload = {
  translations: { vi: string; en?: string }
}

/** Placement title / subtitle / CTA label. */
export type PlacementLocaleText = {
  title: string | null
  subtitle: string | null
  ctaLabel: string | null
}

export type PlacementTranslationsResponse = {
  id: string
  translations: Partial<Record<TranslationLocale, PlacementLocaleText>>
}

export type PlacementTranslationsPayload = {
  translations: {
    vi: { title: string; subtitle: string | null; ctaLabel: string | null }
    en?: { title: string; subtitle: string | null; ctaLabel: string | null }
  }
}

export type EntityTranslationKind = 'product' | 'category'
export type NameTranslationKind = 'strategy' | 'campaign'
