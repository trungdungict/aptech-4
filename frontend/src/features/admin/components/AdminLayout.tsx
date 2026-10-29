import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'

import { useAuth } from '@/features/auth/hooks/useAuth'
import { ChatPanel } from '@/features/chat/components/ChatPanel'
import { NotificationBell } from '@/features/notifications/components/NotificationBell'

import { ADMIN_NAV_ITEMS } from '../nav'

export function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const onLogout = async () => {
    await logout()
    void navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen flex-col bg-background md:flex-row">
      <aside className="flex w-full shrink-0 flex-col border-b border-border bg-card md:w-60 md:border-r md:border-b-0">
        <div className="border-b border-border px-4 py-5">
          <p className="text-xs font-semibold tracking-[0.18em] text-primary uppercase">Aptech-4</p>
          <p className="mt-1 text-sm font-semibold text-foreground">Admin workspace</p>
        </div>

        <nav className="flex-1 px-3 py-4" aria-label="Admin">
          <ul className="space-y-1">
            {ADMIN_NAV_ITEMS.map((item) => {
              if (item.to == null) {
                return (
                  <li key={item.id}>
                    <span
                      className="flex cursor-not-allowed flex-col rounded-lg px-3 py-2 text-sm text-muted-foreground opacity-70"
                      aria-disabled="true"
                      title={item.description}
                    >
                      <span className="font-medium">{item.label}</span>
                      <span className="text-xs">Coming soon</span>
                    </span>
                  </li>
                )
              }

              return (
                <li key={item.id}>
                  <NavLink
                    to={item.to}
                    end={item.to === '/admin'}
                    className={({ isActive }) =>
                      [
                        'block rounded-lg px-3 py-2 text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
                        isActive
                          ? 'bg-primary text-primary-foreground'
                          : 'text-foreground hover:bg-muted',
                      ].join(' ')
                    }
                  >
                    {item.label}
                  </NavLink>
                </li>
              )
            })}
          </ul>
        </nav>

        <div className="space-y-2 border-t border-border px-4 py-4 text-sm">
          {user ? (
            <div className="min-w-0">
              <p className="truncate font-medium text-foreground">{user.fullName}</p>
              <p className="truncate text-xs text-muted-foreground">{user.email}</p>
            </div>
          ) : null}
          <Link
            to="/"
            className="block rounded-lg px-3 py-2 font-medium text-foreground hover:bg-muted focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            Storefront
          </Link>
          <button
            type="button"
            className="w-full rounded-lg border border-border px-3 py-2 text-left font-medium text-foreground hover:bg-muted focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            onClick={() => {
              void onLogout()
            }}
          >
            Sign out
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex items-center justify-between gap-3 border-b border-border bg-card/70 px-4 py-4 backdrop-blur md:px-6">
          <div>
            <p className="text-sm font-medium text-foreground">Admin area</p>
            <p className="text-xs text-muted-foreground">
              Orders, catalog, and operational alerts
            </p>
          </div>
          {user ? <NotificationBell key={user.id} /> : null}
        </div>
        <main className="flex-1 px-4 py-8 md:px-8">
          <Outlet />
        </main>
        {user ? <ChatPanel key={user.id} variant="admin" /> : null}
      </div>
    </div>
  )
}
