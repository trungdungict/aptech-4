import { Link } from 'react-router-dom'

import { ADMIN_NAV_ITEMS } from '../nav'

/**
 * Minimal admin landing. No fabricated metrics or extra API calls.
 */
export function AdminOverviewPage() {
  return (
    <section className="mx-auto max-w-2xl space-y-6">
      <div className="space-y-2">
        <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          Scaffold
        </p>
        <h1 className="text-3xl font-semibold tracking-tight text-foreground">Admin overview</h1>
        <p className="text-sm text-muted-foreground">
          Product, category, and order administration are available. This overview does not show
          fabricated metrics.
        </p>
      </div>

      <ul className="divide-y divide-border rounded-2xl border border-border bg-card">
        {ADMIN_NAV_ITEMS.map((item) => (
          <li key={item.id} className="flex items-start justify-between gap-4 px-4 py-3 text-sm">
            <div>
              <p className="font-medium text-foreground">{item.label}</p>
              <p className="text-muted-foreground">{item.description}</p>
            </div>
            {item.to != null ? (
              <Link
                to={item.to}
                className="shrink-0 font-medium text-primary underline-offset-4 hover:underline"
              >
                Open
              </Link>
            ) : (
              <span className="shrink-0 text-muted-foreground">Soon</span>
            )}
          </li>
        ))}
      </ul>

      <p className="text-xs text-muted-foreground">
        Access here is gated from the trusted auth response roles. Backend{' '}
        <code className="rounded bg-muted px-1">/api/v1/admin/**</code> remains the security
        boundary for data operations.
      </p>
    </section>
  )
}
