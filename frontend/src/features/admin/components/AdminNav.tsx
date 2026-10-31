import { Link, NavLink } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import { navLinkClass } from '@/lib/nav-link-class'
import { cn } from '@/lib/utils'

import { ADMIN_NAV_ITEMS } from '../nav'

type AdminNavProps = {
  /** Accessible name for the landmark. Desktop uses "Admin"; mobile drawer uses "Admin menu". */
  ariaLabel?: string
  onNavigate?: () => void
  onLogout: () => void
  userName?: string | null
  userEmail?: string | null
  className?: string
}

export function AdminNav({
  ariaLabel = 'Admin',
  onNavigate,
  onLogout,
  userName,
  userEmail,
  className,
}: AdminNavProps) {
  return (
    <div className={cn('flex h-full flex-col', className)}>
      <div className="border-b border-border px-4 py-5">
        <p className="text-xs font-semibold tracking-[0.18em] text-primary uppercase">Aptech-4</p>
        <p className="mt-1 text-sm font-semibold text-foreground">Admin workspace</p>
      </div>

      <nav className="flex-1 px-3 py-4" aria-label={ariaLabel}>
        <p className="mb-2 px-3 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          Manage
        </p>
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
                  title={item.description}
                  className={({ isActive }) => navLinkClass(isActive, 'block')}
                  onClick={() => onNavigate?.()}
                >
                  {item.label}
                </NavLink>
              </li>
            )
          })}
        </ul>
      </nav>

      <div className="mt-auto space-y-3 border-t border-border px-4 py-4 text-sm">
        {userName || userEmail ? (
          <div className="min-w-0">
            {userName ? (
              <p className="truncate font-medium text-foreground">{userName}</p>
            ) : null}
            {userEmail ? (
              <p className="truncate text-xs text-muted-foreground">{userEmail}</p>
            ) : null}
          </div>
        ) : null}
        <Separator />
        <div className="space-y-1">
          <Link
            to="/"
            className={navLinkClass(false, 'block')}
            onClick={() => onNavigate?.()}
          >
            Storefront
          </Link>
          <Button
            type="button"
            variant="outline"
            className="w-full justify-start"
            onClick={() => {
              onLogout()
            }}
          >
            Sign out
          </Button>
        </div>
      </div>
    </div>
  )
}
