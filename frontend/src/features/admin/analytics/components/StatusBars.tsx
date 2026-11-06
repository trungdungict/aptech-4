import { useTranslation } from 'react-i18next'

import { useOrderStatusLabel } from '../../orders/lib/use-order-status-label'
import { ORDER_STATUS_DISPLAY_ORDER } from '../types'
import { isKnownOrderStatus } from '../../orders/lib/admin-order-transitions'

type StatusBarsProps = {
  counts: Record<string, number>
  total: number
  empty: boolean
}

export function StatusBars({ counts, total, empty }: StatusBarsProps) {
  const { t } = useTranslation('admin')
  const statusLabel = useOrderStatusLabel()
  const keys = [
    ...ORDER_STATUS_DISPLAY_ORDER.filter((status) => status in counts),
    ...Object.keys(counts).filter(
      (status) => !(ORDER_STATUS_DISPLAY_ORDER as readonly string[]).includes(status),
    ),
  ]
  const max = Math.max(1, ...keys.map((key) => counts[key] ?? 0))

  return (
    <div className="space-y-3">
      {empty ? (
        <p className="rounded-lg border border-dashed border-border px-4 py-5 text-center text-sm text-muted-foreground">
          {t('analytics.status.empty')}
        </p>
      ) : null}
      <p className="text-sm text-muted-foreground">
        {t('analytics.status.totalPlaced')}{' '}
        <span className="font-medium text-foreground tabular-nums">{total}</span>
        <span className="text-xs"> {t('analytics.status.allStatusesInWindow')}</span>
      </p>
      <ul className="space-y-2.5" aria-label={t('analytics.status.countsAria')}>
        {keys.map((status) => {
          const value = counts[status] ?? 0
          const barPct = (value / max) * 100
          const sharePct = total > 0 ? (value / total) * 100 : 0
          return (
            <li key={status} className="space-y-1">
              <div className="flex items-center justify-between gap-3 text-sm">
                <span className="font-medium text-foreground">
                  {isKnownOrderStatus(status) ? statusLabel(status) : status.replaceAll('_', ' ')}
                </span>
                <span className="shrink-0 tabular-nums text-muted-foreground">
                  {value}
                  <span className="ml-1.5 text-xs">({sharePct.toFixed(1)}%)</span>
                </span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-muted" aria-hidden="true">
                <div
                  className="h-full rounded-full bg-primary transition-[width]"
                  style={{ width: `${barPct}%` }}
                />
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
