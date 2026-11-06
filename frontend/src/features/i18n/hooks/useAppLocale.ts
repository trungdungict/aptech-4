import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import {
  changeAppLocale,
  parseAppLocale,
  type AppLocale,
} from '@/lib/i18n'

export function useAppLocale(): {
  locale: AppLocale
  setLocale: (locale: AppLocale) => Promise<void>
} {
  const { i18n } = useTranslation()
  const locale = parseAppLocale(i18n.resolvedLanguage ?? i18n.language)

  const setLocale = useCallback(async (next: AppLocale) => {
    await changeAppLocale(next)
  }, [])

  return { locale, setLocale }
}
