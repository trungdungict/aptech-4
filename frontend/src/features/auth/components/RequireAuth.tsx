import { Navigate, useLocation } from 'react-router-dom'

import { useAuth } from '../hooks/useAuth'

type RequireAuthProps = {
  children: React.ReactNode
}

/** Redirects anonymous users to login with a validated return path. */
export function RequireAuth({ children }: RequireAuthProps) {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'booting') {
    return (
      <p className="text-sm text-muted-foreground" role="status">
        Checking session…
      </p>
    )
  }

  if (status !== 'authenticated') {
    const next = `${location.pathname}${location.search}`
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace state={{ from: location }} />
  }

  return children
}
