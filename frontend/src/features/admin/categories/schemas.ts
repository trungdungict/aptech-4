import { z } from 'zod'

import type { AdminT } from '../lib/admin-i18n'

/** Aligns with backend CategoryCreateRequest / CategoryUpdateRequest (slug is server-generated). */
export function createAdminCategoryFormSchema(t: AdminT) {
  return z.object({
    name: z
      .string()
      .trim()
      .min(1, t('validation.nameRequired'))
      .max(120, t('validation.nameTooLong')),
    description: z
      .string()
      .max(4000, t('validation.descriptionTooLong'))
      .optional()
      .or(z.literal('')),
    parentId: z
      .string()
      .uuid(t('validation.invalidParentCategory'))
      .or(z.literal(''))
      .optional(),
  })
}

export type AdminCategoryFormValues = z.infer<ReturnType<typeof createAdminCategoryFormSchema>>
