import type { ReactNode } from 'react'

import { ProductPrice } from '@/features/catalog/components/ProductPrice'
import { toMoneyNumber } from '@/features/orders/lib/order-money'

export type RankingBarItem = {
  id: string
  rank: number
  title: string
  subtitle?: string
  revenue: string | number
  meta: ReactNode
  action?: ReactNode
}

type RankingBarsProps = {
  items: RankingBarItem[]
  currency: string
  emptyMessage: string
  empty: boolean
  ariaLabel: string
}

export function RankingBars({
  items,
  currency,
  emptyMessage,
  empty,
  ariaLabel,
}: RankingBarsProps) {
  if (empty || items.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-border px-4 py-8 text-center text-sm text-muted-foreground">
        {emptyMessage}
      </p>
    )
  }

  const maxRevenue = Math.max(1, ...items.map((item) => toMoneyNumber(item.revenue)))

  return (
    <ol className="space-y-3" aria-label={ariaLabel}>
      {items.map((item) => {
        const width = (toMoneyNumber(item.revenue) / maxRevenue) * 100
        return (
          <li key={item.id} className="space-y-1.5">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-foreground">
                  <span className="mr-2 inline-flex size-5 items-center justify-center rounded-md bg-muted text-xs tabular-nums text-muted-foreground">
                    {item.rank}
                  </span>
                  {item.title}
                </p>
                {item.subtitle ? (
                  <p className="mt-0.5 truncate font-mono text-xs text-muted-foreground">
                    {item.subtitle}
                  </p>
                ) : null}
              </div>
              <div className="flex shrink-0 flex-col items-end gap-0.5 text-right">
                <ProductPrice
                  unitPrice={item.revenue}
                  currency={currency}
                  className="text-sm font-semibold text-foreground"
                />
                <div className="text-xs text-muted-foreground">{item.meta}</div>
                {item.action}
              </div>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-muted" aria-hidden="true">
              <div
                className="h-full rounded-full bg-primary/80"
                style={{ width: `${width}%` }}
              />
            </div>
          </li>
        )
      })}
    </ol>
  )
}
