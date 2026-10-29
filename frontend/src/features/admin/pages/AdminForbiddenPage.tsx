import { Link } from 'react-router-dom'

import { useAuth } from '@/features/auth/hooks/useAuth'

/** Authenticated non-admin (or unknown role) — fail closed without admin shell. */
export function AdminForbiddenPage() {
  const { user } = useAuth()

  return (
    <div className="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-6 py-16">
      <p className="text-xs font-semibold tracking-[0.18em] text-primary uppercase">Aptech-4</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-foreground">Access denied</h1>
      <p className="mt-3 text-sm text-muted-foreground">
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
        <Link
          to="/"
          className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
        >
          Back to storefront
        </Link>
        <Link
          to="/products"
          className="rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium text-foreground"
        >
          Browse catalog
        </Link>
      </div>
    </div>
  )
}
