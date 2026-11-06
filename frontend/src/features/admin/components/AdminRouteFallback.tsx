import { useTranslation } from 'react-i18next'

/**
 * Shared Suspense fallback for Admin layout and page chunks.
 */
export function AdminRouteFallback() {
  const { t } = useTranslation('admin')
  return (
    <div className="px-4 py-10 md:px-0" role="status" aria-live="polite">
      <p className="text-sm text-muted-foreground">{t('shell.loading')}</p>
    </div>
  )
}
