import { z } from 'zod'

/** Aligns with backend ProductCreateRequest / ProductUpdateRequest. */
export const adminProductFormSchema = z.object({
  sku: z.string().trim().min(1, 'SKU is required').max(64, 'SKU is too long'),
  name: z.string().trim().min(1, 'Name is required').max(200, 'Name is too long'),
  description: z.string().max(10_000, 'Description is too long').optional(),
  unitPrice: z
    .string()
    .trim()
    .min(1, 'Price is required')
    .refine((value) => Number.isFinite(Number(value)) && Number(value) >= 0, {
      message: 'Price must be zero or greater',
    })
    .refine((value) => /^\d+(\.\d{1,2})?$/.test(value), {
      message: 'Use at most 2 decimal places',
    }),
  currency: z
    .string()
    .trim()
    .min(3, 'Currency must be a 3-letter code')
    .max(3, 'Currency must be a 3-letter code'),
  categoryId: z.string().uuid('Select a category'),
  /**
   * Always present in form state as `string | null` (never undefined).
   * Empty/whitespace strings normalize to null for the product API.
   */
  imageUrl: z
    .union([z.string().trim().max(512), z.null()])
    .transform((value) => (value && value.length > 0 ? value : null)),
})

/** Form values after Zod parse (submit payload shape). */
export type AdminProductFormValues = z.output<typeof adminProductFormSchema>
/** Form field state / defaultValues shape before submit transforms. */
export type AdminProductFormInput = z.input<typeof adminProductFormSchema>

/** Absolute quantity_on_hand for Admin inventory PUT (edit page only). */
export const adminInventoryFormSchema = z.object({
  quantityOnHand: z
    .string()
    .trim()
    .min(1, 'Stock quantity is required')
    .refine((value) => /^\d+$/.test(value), {
      message: 'Stock quantity must be a non-negative integer',
    })
    .transform((value) => Number(value)),
})

export type AdminInventoryFormValues = z.output<typeof adminInventoryFormSchema>
export type AdminInventoryFormInput = z.input<typeof adminInventoryFormSchema>
