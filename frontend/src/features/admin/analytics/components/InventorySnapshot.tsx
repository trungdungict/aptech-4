import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'

import type { AdminAnalyticsInventorySummary } from '../types'

type InventorySnapshotProps = {
  inventory: AdminAnalyticsInventorySummary
}

export function InventorySnapshot({ inventory }: InventorySnapshotProps) {
  const { t } = useTranslation('admin')
  const total = Math.max(0, inventory.totalProducts)
  const inStock = Math.max(0, inventory.inStockCount)
  const low = Math.max(0, inventory.lowStockCount)
  const oos = Math.max(0, inventory.outOfStockCount)
  const accounted = inStock + low + oos
  // Prefer exact bucket counts for the stacked bar; if they do not sum to totalProducts
  // (e.g. missing inventory rows), scale visually against accounted while showing exact totals.
  const denom = Math.max(accounted, 1)

  const segments = [
    { key: 'in', label: t('analytics.inventory.inStock'), value: inStock, className: 'bg-emerald-600' },
    { key: 'low', label: t('analytics.inventory.low'), value: low, className: 'bg-amber-500' },
    { key: 'oos', label: t('analytics.inventory.out'), value: oos, className: 'bg-rose-500' },
  ] as const

  return (
    <div className="space-y-4">
      <p className="text-xs text-muted-foreground">
        {t('analytics.inventory.thresholdLine', {
          threshold: inventory.lowStockThreshold,
          definition: inventory.availableDefinition,
        })}
      </p>

      <div
        className="flex h-3 overflow-hidden rounded-full bg-muted"
        role="img"
        aria-label={t('analytics.inventory.distributionAria', { inStock, low, out: oos, total })}
      >
        {segments.map((segment) => {
          const width = (segment.value / denom) * 100
          if (width <= 0) return null
          return (
            <div
              key={segment.key}
              className={segment.className}
              style={{ width: `${width}%` }}
              title={`${segment.label}: ${segment.value}`}
            />
          )
        })}
      </div>

      <ul className="grid gap-2 sm:grid-cols-2">
        <li className="rounded-lg border border-border/80 px-3 py-2">
          <p className="text-xs text-muted-foreground">{t('analytics.inventory.totalProducts')}</p>
          <p className="text-lg font-semibold tabular-nums text-foreground">{total}</p>
        </li>
        {segments.map((segment) => {
          const pct = total > 0 ? (segment.value / total) * 100 : 0
          return (
            <li key={segment.key} className="rounded-lg border border-border/80 px-3 py-2">
              <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                <span className={`inline-block size-2 rounded-sm ${segment.className}`} aria-hidden />
                {segment.label}
              </p>
              <p className="text-lg font-semibold tabular-nums text-foreground">
                {segment.value}
                <span className="ml-1.5 text-xs font-normal text-muted-foreground">
                  ({pct.toFixed(1)}%)
                </span>
              </p>
            </li>
          )
        })}
      </ul>

      <Button asChild variant="link" className="h-auto px-0">
        <Link to="/admin/inventory">{t('analytics.inventory.viewInventory')}</Link>
      </Button>
    </div>
  )
}
