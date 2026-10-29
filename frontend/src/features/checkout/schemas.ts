import { z } from 'zod'

export const addressCreateSchema = z.object({
  label: z.string().max(80).optional(),
  recipientName: z.string().trim().min(1, 'Recipient name is required').max(200),
  line1: z.string().trim().min(1, 'Address line is required').max(200),
  line2: z.string().max(200).optional(),
  city: z.string().trim().min(1, 'City is required').max(120),
  stateRegion: z.string().max(120).optional(),
  postalCode: z.string().max(32).optional(),
  countryCode: z
    .string()
    .trim()
    .refine((value) => value === '' || value.length === 2, 'Use a 2-letter country code')
    .optional(),
  phone: z.string().max(32).optional(),
  defaultAddress: z.boolean().optional(),
})

export type AddressCreateFormValues = z.infer<typeof addressCreateSchema>
