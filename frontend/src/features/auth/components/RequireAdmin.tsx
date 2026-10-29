import { Navigate, useLocation } from 'react-router-dom'

import { hasAdminRole } from '../lib/roles'
import { useAuth } from '../hooks/useAuth'
import { AdminForbiddenPage } from '@/features/admin/pages/AdminForbiddenPage'

type RequireAdminProps = {
  children: React.ReactNode
}

/**
 * UX gate for `/admin/*`. Fail-closed: missing/unknown roles never grant access.
 * Unauthenticated users are sent to login with a safe `next` destination.
 */
export function RequireAdmin({ children }: RequireAdminProps) {
  const { status, user } = useAuth()
  const location = useLocation()

  if (status === 'booting') {
    return (
      <p className="px-6 py-10 text-sm text-muted-foreground" role="status">
        Checking session…
      </p>
    )
  }

  if (status !== 'authenticated') {
    const next = `${location.pathname}${location.search}`
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace state={{ from: location }} />
  }

  if (!hasAdminRole(user?.roles)) {
    return <AdminForbiddenPage />
  }

  return children
}
