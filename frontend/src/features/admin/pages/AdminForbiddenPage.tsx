import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/hooks/useAuth'

import { adminPageLeadClass, adminPageTitleClass } from '../lib/admin-ui'

/** Authenticated non-admin (or unknown role) — fail closed without admin shell. */
export function AdminForbiddenPage() {
  const { t } = useTranslation('admin')
  const { user } = useAuth()

  return (
    <div className="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-6 py-16">
      <p className="text-xs font-semibold tracking-[0.18em] text-primary uppercase">Aptech-4</p>
      <h1 className={`mt-3 ${adminPageTitleClass}`}>{t('forbidden.title')}</h1>
      <p className={`mt-3 ${adminPageLeadClass}`}>
        {t('forbidden.leadBefore')}
        {user?.email ? (
          <>
            {' '}
            (<span className="font-medium text-foreground">{user.email}</span>)
          </>
        ) : null}{' '}
        {t('forbidden.leadAfter')}
      </p>
      <p className="mt-2 text-xs text-muted-foreground">
        {t('forbidden.note')}
      </p>
      <div className="mt-8 flex flex-wrap gap-3">
        <Button asChild>
          <Link to="/">{t('forbidden.backToStorefront')}</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/products">{t('forbidden.browseCatalog')}</Link>
        </Button>
      </div>
    </div>
  )
}
