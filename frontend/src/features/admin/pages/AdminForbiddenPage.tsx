import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/hooks/useAuth'

import { adminPageLeadClass, adminPageTitleClass } from '../lib/admin-ui'

/** Authenticated non-admin (or unknown role) — fail closed without admin shell. */
export function AdminForbiddenPage() {
  const { user } = useAuth()

  return (
    <div className="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-6 py-16">
      <p className="text-xs font-semibold tracking-[0.18em] text-primary uppercase">Aptech-4</p>
      <h1 className={`mt-3 ${adminPageTitleClass}`}>Access denied</h1>
      <p className={`mt-3 ${adminPageLeadClass}`}>
        This area is limited to administrator accounts. Your signed-in session
        {user?.email ? (
          <>
            {' '}
            (<span className="font-medium text-foreground">{user.email}</span>)
          </>
        ) : null}{' '}
        does not include the admin role returned by the API.
      </p>
      <p className="mt-2 text-xs text-muted-foreground">
        Frontend route gating is for UX only. Protected admin APIs remain enforced by the backend.
      </p>
      <div className="mt-8 flex flex-wrap gap-3">
        <Button asChild>
          <Link to="/">Back to storefront</Link>
        </Button>
        <Button asChild variant="outline">
          <Link to="/products">Browse catalog</Link>
        </Button>
      </div>
    </div>
  )
}
