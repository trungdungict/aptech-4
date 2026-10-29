import { z } from 'zod'

/** Aligns with backend CategoryCreateRequest / CategoryUpdateRequest (slug is server-generated). */
export const adminCategoryFormSchema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(120, 'Name is too long'),
  description: z.string().max(4000, 'Description is too long').optional().or(z.literal('')),
  parentId: z
    .string()
    .uuid('Invalid parent category')
    .or(z.literal(''))
    .optional(),
})

export type AdminCategoryFormValues = z.infer<typeof adminCategoryFormSchema>
