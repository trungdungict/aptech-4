import { MenuIcon } from 'lucide-react'
import { Suspense, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'

import { ErrorBoundary } from '@/app/ErrorBoundary'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { LazyChatPanel } from '@/features/chat/components/LazyChatPanel'
import { LanguageSwitcher } from '@/features/i18n/components/LanguageSwitcher'
import { NotificationBell } from '@/features/notifications/components/NotificationBell'
import { useMediaQuery } from '@/hooks/use-media-query'

import { ADMIN_NAV_ITEMS } from '../nav'
import { AdminNav } from './AdminNav'
import { AdminRouteFallback } from './AdminRouteFallback'

function useAdminBreadcrumbLabel(pathname: string): string {
  const { t } = useTranslation('admin')
  return useMemo(() => {
    if (pathname === '/admin' || pathname === '/admin/') {
      return t('nav.overview.label')
    }
    const match = ADMIN_NAV_ITEMS.find(
      (item) => item.to != null && item.to !== '/admin' && pathname.startsWith(item.to),
    )
    return match ? t(`nav.${match.id}.label`) : t('shell.admin')
  }, [pathname, t])
}

export function AdminLayout() {
  const { t } = useTranslation('admin')
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const isDesktop = useMediaQuery('(min-width: 768px)', true)
  const [mobileOpen, setMobileOpen] = useState(false)
  const crumb = useAdminBreadcrumbLabel(location.pathname)

  const onLogout = async () => {
    setMobileOpen(false)
    await logout()
    void navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen bg-background">
      {isDesktop ? (
        <aside className="flex w-60 shrink-0 flex-col border-r border-border bg-card">
          <AdminNav
            ariaLabel={t('shell.navAria')}
            onLogout={() => {
              void onLogout()
            }}
            userName={user?.fullName}
            userEmail={user?.email}
          />
        </aside>
      ) : null}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between gap-3 border-b border-border bg-card/80 px-4 py-3 backdrop-blur md:px-6">
          <div className="flex min-w-0 items-center gap-3">
            {!isDesktop ? (
              <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
                <Button
                  type="button"
                  variant="outline"
                  size="icon"
                  aria-label={t('shell.openMenu')}
                  onClick={() => setMobileOpen(true)}
                >
                  <MenuIcon className="size-4" />
                </Button>
                <SheetContent side="left" className="w-[min(100%,18rem)] p-0 sm:max-w-xs">
                  <SheetHeader className="sr-only">
                    <SheetTitle>{t('shell.navigationTitle')}</SheetTitle>
                    <SheetDescription>{t('shell.navigationDescription')}</SheetDescription>
                  </SheetHeader>
                  <AdminNav
                    ariaLabel={t('shell.menuAria')}
                    onNavigate={() => setMobileOpen(false)}
                    onLogout={() => {
                      void onLogout()
                    }}
                    userName={user?.fullName}
                    userEmail={user?.email}
                  />
                </SheetContent>
              </Sheet>
            ) : null}
            <div className="min-w-0">
              <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
                {t('shell.admin')}
              </p>
              <p className="truncate text-sm font-semibold text-foreground">{crumb}</p>
            </div>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <LanguageSwitcher />
            {user ? <NotificationBell key={user.id} /> : null}
          </div>
        </header>

        <main className="flex-1 px-4 py-8 md:px-8">
          {/* Suspense inside layout so nav/header stay mounted while page chunks load. */}
          <ErrorBoundary key={location.pathname}>
            <Suspense fallback={<AdminRouteFallback />}>
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </main>
        {user ? <LazyChatPanel key={user.id} variant="admin" /> : null}
      </div>
    </div>
  )
}
