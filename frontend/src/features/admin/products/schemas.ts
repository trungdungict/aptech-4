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
})

export type AdminProductFormValues = z.infer<typeof adminProductFormSchema>
