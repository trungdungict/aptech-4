import { Link } from 'react-router-dom'

import { hasAdminRole } from '@/features/auth/lib/roles'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { apiBaseUrl } from '@/lib/env'

/**
 * Minimal landing for the storefront scaffold.
 * Placeholder copy is intentional — no fake catalog data.
 */
export function HomePage() {
  const { status, user } = useAuth()

  return (
    <section className="grid gap-10 md:grid-cols-[1.2fr_0.8fr] md:items-center">
      <div className="space-y-5">
        <p className="text-sm font-medium text-primary">Scaffold / demo content</p>
        <h1 className="max-w-xl text-4xl font-semibold tracking-tight text-foreground md:text-5xl">
          Aptech-4 storefront foundation
        </h1>
        <p className="max-w-prose text-base text-muted-foreground md:text-lg">
          React + TypeScript + Vite with Tailwind, React Router, TanStack Query, and
          authentication against the Spring Boot API.
        </p>
        <ul className="space-y-2 text-sm text-muted-foreground">
          <li>
            API base URL:{' '}
            <code className="break-all rounded bg-muted px-1">{apiBaseUrl}</code>
          </li>
          <li>
            Session:{' '}
            {status === 'booting'
              ? 'restoring…'
              : status === 'authenticated' && user
                ? `${user.email} (${user.roles.join(', ')})`
                : 'signed out'}
          </li>
        </ul>
        <div className="flex flex-wrap gap-3">
          <Link
            to="/products"
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
          >
            Browse catalog
          </Link>
          {status === 'anonymous' ? (
            <>
              <Link
                to="/login"
                className="rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium text-foreground"
              >
                Sign in
              </Link>
              <Link
                to="/register"
                className="rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium text-foreground"
              >
                Register
              </Link>
            </>
          ) : null}
        </div>
      </div>

      <aside className="rounded-2xl border border-border bg-card p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-card-foreground">Customer flows</h2>
        <ol className="mt-4 list-decimal space-y-2 pl-5 text-sm text-muted-foreground">
          <li>
            <Link className="text-primary underline-offset-4 hover:underline" to="/cart">
              Cart
            </Link>
            {' · '}
            <Link className="text-primary underline-offset-4 hover:underline" to="/checkout">
              Checkout
            </Link>
            {' · '}
            <Link className="text-primary underline-offset-4 hover:underline" to="/orders">
              Orders
            </Link>{' '}
            (sign-in required)
          </li>
          <li>
            {status === 'authenticated' && hasAdminRole(user?.roles) ? (
              <Link className="text-primary underline-offset-4 hover:underline" to="/admin">
                Admin workspace
              </Link>
            ) : (
              <>Admin workspace</>
            )}{' '}
            (ADMIN role from auth API; UX gate only)
          </li>
        </ol>
      </aside>
    </section>
  )
}
