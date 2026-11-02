import { ORDER_STATUS_DISPLAY_ORDER } from '../types'

type StatusBarsProps = {
  counts: Record<string, number>
  total: number
  empty: boolean
}

export function StatusBars({ counts, total, empty }: StatusBarsProps) {
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
          No orders in this range. All statuses are zero.
        </p>
      ) : null}
      <p className="text-sm text-muted-foreground">
        Total placed:{' '}
        <span className="font-medium text-foreground tabular-nums">{total}</span>
        <span className="text-xs"> (all statuses in window)</span>
      </p>
      <ul className="space-y-2.5" aria-label="Order counts by status">
        {keys.map((status) => {
          const value = counts[status] ?? 0
          const barPct = (value / max) * 100
          const sharePct = total > 0 ? (value / total) * 100 : 0
          return (
            <li key={status} className="space-y-1">
              <div className="flex items-center justify-between gap-3 text-sm">
                <span className="font-medium text-foreground">
                  {status.replaceAll('_', ' ')}
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
