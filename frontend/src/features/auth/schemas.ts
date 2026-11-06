import type { TFunction } from 'i18next'
import { z } from 'zod'

/** Translator scoped to the `validation` namespace (from `useTranslation('validation')`). */
export type ValidationT = TFunction<'validation'>

/** Aligns with backend `LoginRequest` validation. Messages are resolved via `t` at validation time. */
export function createLoginSchema(t: ValidationT) {
  return z.object({
    email: z
      .string()
      .trim()
      .min(1, t('emailRequired'))
      .email(t('emailInvalid'))
      .max(320, t('emailTooLong')),
    password: z
      .string()
      .min(1, t('passwordRequired'))
      .max(72, t('passwordTooLong')),
  })
}

/** Aligns with backend `RegisterRequest` — no role field (CUSTOMER is server-assigned). */
export function createRegisterSchema(t: ValidationT) {
  return z.object({
    email: z
      .string()
      .trim()
      .min(1, t('emailRequired'))
      .email(t('emailInvalid'))
      .max(320, t('emailTooLong')),
    fullName: z
      .string()
      .trim()
      .min(1, t('fullNameRequired'))
      .max(200, t('fullNameTooLong')),
    password: z
      .string()
      .min(8, t('passwordMin'))
      .max(72, t('passwordMax')),
  })
}

export type LoginFormValues = z.infer<ReturnType<typeof createLoginSchema>>
export type RegisterFormValues = z.infer<ReturnType<typeof createRegisterSchema>>
