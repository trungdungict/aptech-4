import { z } from 'zod'

import type { AdminT } from '../lib/admin-i18n'

function blank(value: string | undefined): boolean {
  return value == null || value.trim() === ''
}

type EntityLimits = { nameMax: number; descriptionMax: number }

/** Product / category: `translations.vi.name` required; EN optional (EN description needs EN name). */
export function createEntityTranslationsSchema(t: AdminT, limits: EntityLimits) {
  const name = z.string().trim().max(limits.nameMax, t('validation.nameTooLong'))
  const description = z.string().max(limits.descriptionMax, t('validation.descriptionTooLong'))
  return z
    .object({
      viName: name.min(1, t('validation.viRequired')),
      viDescription: description,
      enName: name,
      enDescription: description,
    })
    .superRefine((values, ctx) => {
      if (blank(values.enName) && !blank(values.enDescription)) {
        ctx.addIssue({
          code: 'custom',
          path: ['enName'],
          message: t('validation.enNameRequired'),
        })
      }
    })
}

export type EntityTranslationsFormValues = z.output<
  ReturnType<typeof createEntityTranslationsSchema>
>

/** Strategy / campaign: VI name required; EN optional. */
export function createNameTranslationsSchema(t: AdminT, nameMax: number) {
  const name = z.string().trim().max(nameMax, t('validation.nameTooLong'))
  return z.object({
    viName: name.min(1, t('validation.viRequired')),
    enName: name,
  })
}

export type NameTranslationsFormValues = z.output<ReturnType<typeof createNameTranslationsSchema>>

/** Placement: VI title required; EN optional (EN subtitle/CTA need EN title). */
export function createPlacementTranslationsSchema(t: AdminT) {
  const title = z.string().trim().max(160, t('validation.textTooLong'))
  const subtitle = z.string().max(255, t('validation.textTooLong'))
  const ctaLabel = z.string().max(80, t('validation.textTooLong'))
  return z
    .object({
      viTitle: title.min(1, t('validation.viRequired')),
      viSubtitle: subtitle,
      viCtaLabel: ctaLabel,
      enTitle: title,
      enSubtitle: subtitle,
      enCtaLabel: ctaLabel,
    })
    .superRefine((values, ctx) => {
      if (blank(values.enTitle) && (!blank(values.enSubtitle) || !blank(values.enCtaLabel))) {
        ctx.addIssue({
          code: 'custom',
          path: ['enTitle'],
          message: t('validation.enTitleRequired'),
        })
      }
    })
}

export type PlacementTranslationsFormValues = z.output<
  ReturnType<typeof createPlacementTranslationsSchema>
>
