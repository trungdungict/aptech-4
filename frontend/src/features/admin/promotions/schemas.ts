import { z } from 'zod'

import { CTA_TYPES, PLACEMENT_KEYS, STRATEGY_TYPES } from './types'
import { isValidUtcInstantInput } from './lib/format-utc'

function optionalNumberString(label: string) {
  return z
    .string()
    .optional()
    .transform((value, ctx) => {
      if (value == null || value.trim() === '') {
        return null
      }
      const n = Number(value)
      if (!Number.isFinite(n)) {
        ctx.addIssue({ code: 'custom', message: `${label} must be a number` })
        return z.NEVER
      }
      return n
    })
}

export const adminStrategyFormSchema = z
  .object({
    name: z.string().trim().min(1, 'Name is required').max(120),
    code: z.string().max(64).optional(),
    type: z.enum(STRATEGY_TYPES),
    discountPercent: optionalNumberString('discountPercent'),
    discountAmount: optionalNumberString('discountAmount'),
    maxDiscountAmount: optionalNumberString('maxDiscountAmount'),
    minOrderAmount: optionalNumberString('minOrderAmount'),
    minQuantity: optionalNumberString('minQuantity'),
    stackable: z.boolean(),
    priority: z
      .string()
      .trim()
      .min(1, 'Priority is required')
      .refine((value) => /^-?\d+$/.test(value), { message: 'Priority must be an integer' })
      .transform((value) => Number(value)),
    active: z.boolean(),
  })
  .superRefine((values, ctx) => {
    if (values.type === 'BANNER_ONLY') {
      if (values.discountPercent != null || values.discountAmount != null || values.maxDiscountAmount != null) {
        ctx.addIssue({
          code: 'custom',
          path: ['type'],
          message: 'BANNER_ONLY cannot define discount values',
        })
      }
      return
    }
    if (values.type === 'PERCENTAGE_DISCOUNT' && values.discountPercent == null) {
      ctx.addIssue({ code: 'custom', path: ['discountPercent'], message: 'discountPercent is required' })
    }
    if (values.type === 'FIXED_AMOUNT_DISCOUNT' && values.discountAmount == null) {
      ctx.addIssue({ code: 'custom', path: ['discountAmount'], message: 'discountAmount is required' })
    }
    if (
      (values.type === 'PRODUCT_DISCOUNT' || values.type === 'CATEGORY_DISCOUNT')
      && (values.discountPercent == null) === (values.discountAmount == null)
    ) {
      ctx.addIssue({
        code: 'custom',
        path: ['discountPercent'],
        message: 'Provide exactly one of discountPercent or discountAmount',
      })
    }
  })

export type AdminStrategyFormValues = z.output<typeof adminStrategyFormSchema>
export type AdminStrategyFormInput = z.input<typeof adminStrategyFormSchema>

export const adminCampaignDetailsSchema = z
  .object({
    strategyId: z.string().uuid('Select a strategy'),
    name: z.string().trim().min(1, 'Name is required').max(160),
    slug: z.string().max(160).optional(),
    startsAt: z.string().trim().min(1, 'startsAt (UTC) is required'),
    endsAt: z.string().trim().min(1, 'endsAt (UTC) is required'),
    priority: z
      .string()
      .trim()
      .min(1, 'Priority is required')
      .refine((value) => /^-?\d+$/.test(value), { message: 'Priority must be an integer' })
      .transform((value) => Number(value)),
    stackable: z.boolean(),
    notes: z.string().optional(),
  })
  .superRefine((values, ctx) => {
    if (!isValidUtcInstantInput(values.startsAt)) {
      ctx.addIssue({
        code: 'custom',
        path: ['startsAt'],
        message: 'Use ISO-8601 UTC with Z or offset (e.g. 2026-11-01T00:00:00Z)',
      })
    }
    if (!isValidUtcInstantInput(values.endsAt)) {
      ctx.addIssue({
        code: 'custom',
        path: ['endsAt'],
        message: 'Use ISO-8601 UTC with Z or offset (e.g. 2026-11-07T23:59:59Z)',
      })
    }
  })

export type AdminCampaignDetailsValues = z.output<typeof adminCampaignDetailsSchema>
export type AdminCampaignDetailsInput = z.input<typeof adminCampaignDetailsSchema>

export const adminPlacementFormSchema = z
  .object({
    placementKey: z.enum(PLACEMENT_KEYS),
    title: z.string().trim().min(1, 'Title is required').max(160),
    subtitle: z.string().max(255).optional(),
    ctaLabel: z.string().max(80).optional(),
    ctaType: z.enum(CTA_TYPES),
    ctaProductId: z.string().optional(),
    ctaCategoryId: z.string().optional(),
    ctaCollectionKey: z.string().max(80).optional(),
    ctaUrl: z.string().max(512).optional(),
    desktopImageUrl: z.string().trim().min(1, 'Desktop image path is required'),
    mobileImageUrl: z.string().optional(),
    displayOrder: z
      .string()
      .trim()
      .min(1, 'Display order is required')
      .refine((value) => /^-?\d+$/.test(value), { message: 'Display order must be an integer' })
      .transform((value) => Number(value)),
    startsAt: z.string().optional(),
    endsAt: z.string().optional(),
    enabled: z.boolean(),
  })
  .superRefine((values, ctx) => {
    if (values.ctaType === 'PRODUCT' && !values.ctaProductId?.trim()) {
      ctx.addIssue({ code: 'custom', path: ['ctaProductId'], message: 'Product CTA requires a product' })
    }
    if (values.ctaType === 'CATEGORY' && !values.ctaCategoryId?.trim()) {
      ctx.addIssue({ code: 'custom', path: ['ctaCategoryId'], message: 'Category CTA requires a category' })
    }
    if (values.ctaType === 'COLLECTION' && !values.ctaCollectionKey?.trim()) {
      ctx.addIssue({ code: 'custom', path: ['ctaCollectionKey'], message: 'Collection key required' })
    }
    if (values.ctaType === 'URL') {
      const url = values.ctaUrl?.trim() ?? ''
      if (!url) {
        ctx.addIssue({ code: 'custom', path: ['ctaUrl'], message: 'URL is required' })
      } else if (!/^https?:\/\//i.test(url) && !(url.startsWith('/') && !url.startsWith('//'))) {
        ctx.addIssue({
          code: 'custom',
          path: ['ctaUrl'],
          message: 'URL must be http(s) or a path starting with /',
        })
      }
    }
    if (values.startsAt?.trim() && !isValidUtcInstantInput(values.startsAt)) {
      ctx.addIssue({ code: 'custom', path: ['startsAt'], message: 'Optional startsAt must be UTC ISO-8601' })
    }
    if (values.endsAt?.trim() && !isValidUtcInstantInput(values.endsAt)) {
      ctx.addIssue({ code: 'custom', path: ['endsAt'], message: 'Optional endsAt must be UTC ISO-8601' })
    }
    if (!values.desktopImageUrl.startsWith('/uploads/promotions/')) {
      ctx.addIssue({
        code: 'custom',
        path: ['desktopImageUrl'],
        message: 'desktopImageUrl must be under /uploads/promotions/',
      })
    }
  })

export type AdminPlacementFormValues = z.output<typeof adminPlacementFormSchema>
export type AdminPlacementFormInput = z.input<typeof adminPlacementFormSchema>
