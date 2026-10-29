import { Link, Outlet } from 'react-router-dom'

import { useAuth } from '@/features/auth/hooks/useAuth'
import { NotificationBell } from '@/features/notifications/components/NotificationBell'

export function RootLayout() {
  const { status, user, logout } = useAuth()

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-b border-border/80 bg-card/70 backdrop-blur">
        <div className="mx-auto flex w-full max-w-5xl items-center justify-between gap-4 px-6 py-4">
          <Link to="/" className="min-w-0">
            <p className="text-xs font-semibold tracking-[0.18em] text-primary uppercase">
              Aptech-4
            </p>
            <p className="truncate text-sm text-muted-foreground">Customer storefront</p>
          </Link>

          <nav className="flex shrink-0 items-center gap-2 text-sm">
            <Link
              className="rounded-lg px-3 py-1.5 font-medium text-foreground hover:bg-muted"
              to="/products"
            >
              Catalog
            </Link>
            {status === 'booting' ? (
              <span className="text-muted-foreground">Loading…</span>
            ) : status === 'authenticated' && user ? (
              <>
                <Link
                  className="rounded-lg px-3 py-1.5 font-medium text-foreground hover:bg-muted"
                  to="/cart"
                >
                  Cart
                </Link>
                <Link
                  className="rounded-lg px-3 py-1.5 font-medium text-foreground hover:bg-muted"
                  to="/orders"
                >
                  Orders
                </Link>
                <NotificationBell key={user.id} />
                <span className="hidden max-w-[12rem] truncate text-muted-foreground sm:inline">
                  {user.fullName}
                </span>
                <button
                  type="button"
                  className="rounded-lg border border-border bg-card px-3 py-1.5 font-medium text-foreground hover:bg-muted"
                  onClick={() => {
                    void logout()
                  }}
                >
                  Sign out
                </button>
              </>
            ) : (
              <>
                <Link
                  className="rounded-lg px-3 py-1.5 font-medium text-foreground hover:bg-muted"
                  to="/login"
                >
                  Sign in
                </Link>
                <Link
                  className="rounded-lg bg-primary px-3 py-1.5 font-medium text-primary-foreground"
                  to="/register"
                >
                  Register
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-6 py-10">
        <Outlet />
      </main>
      <footer className="border-t border-border/80 py-6 text-center text-xs text-muted-foreground">
        Customer cart, mock checkout, and orders — payment is demo-only.
      </footer>
    </div>
  )
}
