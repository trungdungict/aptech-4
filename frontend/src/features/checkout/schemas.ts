import type { TFunction } from 'i18next'
import { z } from 'zod'

/** Address form — messages resolved via `validation` namespace `t` at validation time. */
export function createAddressCreateSchema(t: TFunction<'validation'>) {
  return z.object({
    label: z.string().max(80).optional(),
    recipientName: z.string().trim().min(1, t('recipientRequired')).max(200),
    line1: z.string().trim().min(1, t('addressLineRequired')).max(200),
    line2: z.string().max(200).optional(),
    city: z.string().trim().min(1, t('cityRequired')).max(120),
    stateRegion: z.string().max(120).optional(),
    postalCode: z.string().max(32).optional(),
    countryCode: z
      .string()
      .trim()
      .refine((value) => value === '' || value.length === 2, t('countryCode'))
      .optional(),
    phone: z.string().max(32).optional(),
    defaultAddress: z.boolean().optional(),
  })
}

export type AddressCreateFormValues = z.infer<ReturnType<typeof createAddressCreateSchema>>
