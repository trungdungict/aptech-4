import {
  ArrowRightIcon,
  FolderTreeIcon,
  LayoutDashboardIcon,
  PackageIcon,
  ShoppingBagIcon,
  type LucideIcon,
} from 'lucide-react'
import { Link } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

import { adminPageLeadClass, adminPageSectionClass, adminPageTitleClass } from '../lib/admin-ui'
import { ADMIN_NAV_ITEMS, type AdminNavItem } from '../nav'

const MODULE_ICONS: Record<string, LucideIcon> = {
  overview: LayoutDashboardIcon,
  products: PackageIcon,
  categories: FolderTreeIcon,
  orders: ShoppingBagIcon,
}

/**
 * Admin SaaS landing — module links only; no fabricated KPIs or analytics.
 */
export function AdminOverviewPage() {
  return (
    <section className={`mx-auto max-w-5xl ${adminPageSectionClass}`}>
      <div className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="secondary">Scaffold</Badge>
          <Badge variant="outline">Admin workspace</Badge>
        </div>
        <h1 className={adminPageTitleClass}>Admin overview</h1>
        <p className={`max-w-2xl ${adminPageLeadClass}`}>
          Manage catalog and fulfillment modules available in this workspace. Product, category,
          and order administration are live; this page does not show fabricated metrics.
        </p>
      </div>

      <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-2">
        {ADMIN_NAV_ITEMS.map((item) => (
          <li key={item.id}>
            <AdminModuleCard item={item} />
          </li>
        ))}
      </ul>

      <p className="text-xs text-muted-foreground">
        Access is gated from trusted auth response roles. Backend admin APIs remain the security
        boundary for data operations.
      </p>
    </section>
  )
}

function AdminModuleCard({ item }: { item: AdminNavItem }) {
  const Icon = MODULE_ICONS[item.id] ?? LayoutDashboardIcon
  const isCurrent = item.to === '/admin'
  const available = item.to != null

  const body = (
    <>
      <CardHeader className="flex flex-row items-start gap-3 space-y-0">
        <span
          className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary"
          aria-hidden="true"
        >
          <Icon className="size-5" />
        </span>
        <div className="min-w-0 flex-1 space-y-1.5">
          <CardTitle>
            <h2 className="text-base font-semibold text-foreground">{item.label}</h2>
          </CardTitle>
          <CardDescription>{item.description}</CardDescription>
        </div>
      </CardHeader>
      <CardContent className="pt-0">
        {available ? (
          isCurrent ? (
            <Badge variant="success">You are here</Badge>
          ) : (
            <span className="inline-flex items-center gap-1 text-sm font-medium text-primary">
              Open
              <ArrowRightIcon className="size-4" aria-hidden="true" />
            </span>
          )
        ) : (
          <span className="text-sm text-muted-foreground">Soon</span>
        )}
      </CardContent>
    </>
  )

  if (!available) {
    return (
      <Card className="h-full opacity-70 shadow-sm">
        {body}
      </Card>
    )
  }

  if (isCurrent) {
    return (
      <Card className="h-full border-primary/40 shadow-sm" aria-current="page">
        {body}
      </Card>
    )
  }

  return (
    <Link
      to={item.to!}
      className="block h-full rounded-xl focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      aria-label={`Open ${item.label}`}
    >
      <Card className="h-full shadow-sm transition hover:border-primary/40 hover:shadow-md">
        {body}
      </Card>
    </Link>
  )
}
