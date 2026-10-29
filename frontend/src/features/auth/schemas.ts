import { z } from 'zod'

/** Aligns with backend `LoginRequest` validation. */
export const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Email is required')
    .email('Enter a valid email')
    .max(320, 'Email is too long'),
  password: z
    .string()
    .min(1, 'Password is required')
    .max(72, 'Password is too long'),
})

/** Aligns with backend `RegisterRequest` — no role field (CUSTOMER is server-assigned). */
export const registerSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Email is required')
    .email('Enter a valid email')
    .max(320, 'Email is too long'),
  fullName: z
    .string()
    .trim()
    .min(1, 'Full name is required')
    .max(200, 'Full name is too long'),
  password: z
    .string()
    .min(8, 'Password must be at least 8 characters')
    .max(72, 'Password must be at most 72 characters'),
})

export type LoginFormValues = z.infer<typeof loginSchema>
export type RegisterFormValues = z.infer<typeof registerSchema>
