import { z } from 'zod'

import { CTA_TYPES, PLACEMENT_KEYS, STRATEGY_TYPES } from './types'
import { isValidUtcInstantInput } from './lib/format-utc'
import type { AdminT } from '../lib/admin-i18n'

function optionalNumberString(t: AdminT, label: string) {
  return z
    .string()
    .optional()
    .transform((value, ctx) => {
      if (value == null || value.trim() === '') {
        return null
      }
      const n = Number(value)
      if (!Number.isFinite(n)) {
        ctx.addIssue({ code: 'custom', message: t('validation.mustBeNumber', { label }) })
        return z.NEVER
      }
      return n
    })
}

export function createAdminStrategyFormSchema(t: AdminT) {
  return z
  .object({
    name: z.string().trim().min(1, t('validation.nameRequired')).max(120),
    code: z.string().max(64).optional(),
    type: z.enum(STRATEGY_TYPES),
    discountPercent: optionalNumberString(t, 'discountPercent'),
    discountAmount: optionalNumberString(t, 'discountAmount'),
    maxDiscountAmount: optionalNumberString(t, 'maxDiscountAmount'),
    minOrderAmount: optionalNumberString(t, 'minOrderAmount'),
    minQuantity: optionalNumberString(t, 'minQuantity'),
    stackable: z.boolean(),
    priority: z
      .string()
      .trim()
      .min(1, t('validation.prioritySchemaRequired'))
      .refine((value) => /^-?\d+$/.test(value), { message: t('validation.priorityInteger') })
      .transform((value) => Number(value)),
    active: z.boolean(),
  })
  .superRefine((values, ctx) => {
    if (values.type === 'BANNER_ONLY') {
      if (values.discountPercent != null || values.discountAmount != null || values.maxDiscountAmount != null) {
        ctx.addIssue({
          code: 'custom',
          path: ['type'],
          message: t('validation.bannerNoDiscount'),
        })
      }
      return
    }
    if (values.type === 'PERCENTAGE_DISCOUNT' && values.discountPercent == null) {
      ctx.addIssue({ code: 'custom', path: ['discountPercent'], message: t('validation.discountPercentRequired') })
    }
    if (values.type === 'FIXED_AMOUNT_DISCOUNT' && values.discountAmount == null) {
      ctx.addIssue({ code: 'custom', path: ['discountAmount'], message: t('validation.discountAmountRequired') })
    }
    if (
      (values.type === 'PRODUCT_DISCOUNT' || values.type === 'CATEGORY_DISCOUNT')
      && (values.discountPercent == null) === (values.discountAmount == null)
    ) {
      ctx.addIssue({
        code: 'custom',
        path: ['discountPercent'],
        message: t('validation.exactlyOneDiscount'),
      })
    }
  })
}

export type AdminStrategyFormValues = z.output<ReturnType<typeof createAdminStrategyFormSchema>>
export type AdminStrategyFormInput = z.input<ReturnType<typeof createAdminStrategyFormSchema>>

export function createAdminCampaignDetailsSchema(t: AdminT) {
  return z
  .object({
    strategyId: z.string().uuid(t('validation.selectStrategy')),
    name: z.string().trim().min(1, t('validation.nameRequired')).max(160),
    slug: z.string().max(160).optional(),
    startsAt: z.string().trim().min(1, t('validation.startsAtRequired')),
    endsAt: z.string().trim().min(1, t('validation.endsAtRequired')),
    priority: z
      .string()
      .trim()
      .min(1, t('validation.prioritySchemaRequired'))
      .refine((value) => /^-?\d+$/.test(value), { message: t('validation.priorityInteger') })
      .transform((value) => Number(value)),
    stackable: z.boolean(),
    notes: z.string().optional(),
  })
  .superRefine((values, ctx) => {
    if (!isValidUtcInstantInput(values.startsAt)) {
      ctx.addIssue({
        code: 'custom',
        path: ['startsAt'],
        message: t('validation.startsAtFormat'),
      })
    }
    if (!isValidUtcInstantInput(values.endsAt)) {
      ctx.addIssue({
        code: 'custom',
        path: ['endsAt'],
        message: t('validation.endsAtFormat'),
      })
    }
  })
}

export type AdminCampaignDetailsValues = z.output<ReturnType<typeof createAdminCampaignDetailsSchema>>
export type AdminCampaignDetailsInput = z.input<ReturnType<typeof createAdminCampaignDetailsSchema>>

export function createAdminPlacementFormSchema(t: AdminT) {
  return z
  .object({
    placementKey: z.enum(PLACEMENT_KEYS),
    title: z.string().trim().min(1, t('validation.titleRequired')).max(160),
    subtitle: z.string().max(255).optional(),
    ctaLabel: z.string().max(80).optional(),
    ctaType: z.enum(CTA_TYPES),
    ctaProductId: z.string().optional(),
    ctaCategoryId: z.string().optional(),
    ctaCollectionKey: z.string().max(80).optional(),
    ctaUrl: z.string().max(512).optional(),
    desktopImageUrl: z.string().trim().min(1, t('validation.desktopImageRequired')),
    mobileImageUrl: z.string().optional(),
    displayOrder: z
      .string()
      .trim()
      .min(1, t('validation.displayOrderRequired'))
      .refine((value) => /^-?\d+$/.test(value), { message: t('validation.displayOrderInteger') })
      .transform((value) => Number(value)),
    startsAt: z.string().optional(),
    endsAt: z.string().optional(),
    enabled: z.boolean(),
  })
  .superRefine((values, ctx) => {
    if (values.ctaType === 'PRODUCT' && !values.ctaProductId?.trim()) {
      ctx.addIssue({ code: 'custom', path: ['ctaProductId'], message: t('validation.ctaProductRequired') })
    }
    if (values.ctaType === 'CATEGORY' && !values.ctaCategoryId?.trim()) {
      ctx.addIssue({ code: 'custom', path: ['ctaCategoryId'], message: t('validation.ctaCategoryRequired') })
    }
    if (values.ctaType === 'COLLECTION' && !values.ctaCollectionKey?.trim()) {
      ctx.addIssue({ code: 'custom', path: ['ctaCollectionKey'], message: t('validation.ctaCollectionRequired') })
    }
    if (values.ctaType === 'URL') {
      const url = values.ctaUrl?.trim() ?? ''
      if (!url) {
        ctx.addIssue({ code: 'custom', path: ['ctaUrl'], message: t('validation.ctaUrlRequired') })
      } else if (!/^https?:\/\//i.test(url) && !(url.startsWith('/') && !url.startsWith('//'))) {
        ctx.addIssue({
          code: 'custom',
          path: ['ctaUrl'],
          message: t('validation.ctaUrlFormat'),
        })
      }
    }
    if (values.startsAt?.trim() && !isValidUtcInstantInput(values.startsAt)) {
      ctx.addIssue({ code: 'custom', path: ['startsAt'], message: t('validation.optionalStartsAt') })
    }
    if (values.endsAt?.trim() && !isValidUtcInstantInput(values.endsAt)) {
      ctx.addIssue({ code: 'custom', path: ['endsAt'], message: t('validation.optionalEndsAt') })
    }
    if (!values.desktopImageUrl.startsWith('/uploads/promotions/')) {
      ctx.addIssue({
        code: 'custom',
        path: ['desktopImageUrl'],
        message: t('validation.desktopImagePath'),
      })
    }
  })
}

export type AdminPlacementFormValues = z.output<ReturnType<typeof createAdminPlacementFormSchema>>
export type AdminPlacementFormInput = z.input<ReturnType<typeof createAdminPlacementFormSchema>>
