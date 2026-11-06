import { z } from 'zod'

import type { AdminT } from '../lib/admin-i18n'

/** Aligns with backend ProductCreateRequest / ProductUpdateRequest. Messages resolved via `t`. */
export function createAdminProductFormSchema(t: AdminT) {
  return z.object({
    sku: z.string().trim().min(1, t('validation.skuRequired')).max(64, t('validation.skuTooLong')),
    name: z
      .string()
      .trim()
      .min(1, t('validation.nameRequired'))
      .max(200, t('validation.nameTooLong')),
    description: z.string().max(10_000, t('validation.descriptionTooLong')).optional(),
    unitPrice: z
      .string()
      .trim()
      .min(1, t('validation.priceRequired'))
      .refine((value) => Number.isFinite(Number(value)) && Number(value) >= 0, {
        message: t('validation.priceNonNegative'),
      })
      .refine((value) => /^\d+(\.\d{1,2})?$/.test(value), {
        message: t('validation.priceDecimals'),
      }),
    currency: z
      .string()
      .trim()
      .min(3, t('validation.currencyCode'))
      .max(3, t('validation.currencyCode')),
    categoryId: z.string().uuid(t('validation.selectCategory')),
    /**
     * Always present in form state as `string | null` (never undefined).
     * Empty/whitespace strings normalize to null for the product API.
     */
    imageUrl: z
      .union([z.string().trim().max(512), z.null()])
      .transform((value) => (value && value.length > 0 ? value : null)),
  })
}

type ProductFormSchema = ReturnType<typeof createAdminProductFormSchema>

/** Form values after Zod parse (submit payload shape). */
export type AdminProductFormValues = z.output<ProductFormSchema>
/** Form field state / defaultValues shape before submit transforms. */
export type AdminProductFormInput = z.input<ProductFormSchema>

/** Absolute quantity_on_hand for Admin inventory PUT (edit page only). */
export function createAdminInventoryFormSchema(t: AdminT) {
  return z.object({
    quantityOnHand: z
      .string()
      .trim()
      .min(1, t('validation.stockRequired'))
      .refine((value) => /^\d+$/.test(value), {
        message: t('validation.stockInteger'),
      })
      .transform((value) => Number(value)),
  })
}

type InventoryFormSchema = ReturnType<typeof createAdminInventoryFormSchema>

export type AdminInventoryFormValues = z.output<InventoryFormSchema>
export type AdminInventoryFormInput = z.input<InventoryFormSchema>
