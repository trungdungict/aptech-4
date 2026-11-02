import { useId, useMemo, useState } from 'react'

import { ProductPrice } from '@/features/catalog/components/ProductPrice'
import { toMoneyNumber } from '@/features/orders/lib/order-money'

import type { AdminAnalyticsTimeseriesPoint } from '../types'

type TimeseriesChartProps = {
  points: AdminAnalyticsTimeseriesPoint[]
  currency: string
  empty: boolean
}

/**
 * Revenue as bars (left scale) + placed orders as a line (right scale).
 * CSS/SVG only — tooltips via hover state; exact values in the table below.
 */
export function TimeseriesChart({ points, currency, empty }: TimeseriesChartProps) {
  const gradId = useId().replace(/:/g, '')
  const [hoverIndex, setHoverIndex] = useState<number | null>(null)

  const { maxRevenue, maxPlaced, activeDays } = useMemo(() => {
    const revenues = points.map((p) => toMoneyNumber(p.revenueTotal))
    const placed = points.map((p) => p.placedOrderCount)
    return {
      maxRevenue: Math.max(1, ...revenues),
      maxPlaced: Math.max(1, ...placed),
      activeDays: points.filter((p) => p.placedOrderCount > 0 || toMoneyNumber(p.revenueTotal) > 0)
        .length,
    }
  }, [points])

  const width = 640
  const height = 220
  const padL = 48
  const padR = 48
  const padT = 16
  const padB = 36
  const plotW = width - padL - padR
  const plotH = height - padT - padB
  const n = Math.max(points.length, 1)
  const slot = plotW / n

  const linePoints = points
    .map((point, index) => {
      const cx = padL + index * slot + slot / 2
      const cy = padT + plotH - (point.placedOrderCount / maxPlaced) * plotH
      return `${cx},${cy}`
    })
    .join(' ')

  const hover = hoverIndex != null ? points[hoverIndex] : null

  return (
    <div className="space-y-3">
      {empty ? (
        <p className="rounded-lg border border-dashed border-border px-4 py-5 text-center text-sm text-muted-foreground">
          No orders in this range. Daily values are zero-filled.
        </p>
      ) : null}

      <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-muted-foreground">
        <div className="flex flex-wrap gap-4">
          <span className="inline-flex items-center gap-1.5">
            <span className="inline-block size-2.5 rounded-sm bg-primary/80" aria-hidden="true" />
            Revenue (bars)
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="inline-block size-2.5 rounded-full bg-teal-700" aria-hidden="true" />
            Placed orders (line)
          </span>
        </div>
        <p className="tabular-nums">
          {activeDays}/{points.length} days with activity
        </p>
      </div>

      <div className="relative overflow-x-auto rounded-xl border border-border/80 bg-card p-3 shadow-sm">
        {hover ? (
          <div
            className="pointer-events-none absolute top-3 right-3 z-10 max-w-[14rem] rounded-lg border border-border bg-background/95 px-3 py-2 text-xs shadow-md"
            role="status"
          >
            <p className="font-mono font-medium text-foreground">{hover.date}</p>
            <p className="mt-1 text-muted-foreground">
              Placed: <span className="tabular-nums text-foreground">{hover.placedOrderCount}</span>
            </p>
            <p className="text-muted-foreground">
              Revenue:{' '}
              <ProductPrice
                unitPrice={hover.revenueTotal}
                currency={currency}
                className="inline text-xs font-medium text-foreground"
              />
            </p>
          </div>
        ) : null}

        <svg
          role="img"
          aria-label="Daily revenue bars and placed-order line"
          viewBox={`0 0 ${width} ${height}`}
          className="h-56 w-full min-w-[20rem]"
        >
          <title>Daily revenue and placed orders</title>
          <defs>
            <linearGradient id={`rev-${gradId}`} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="hsl(var(--primary))" stopOpacity="0.85" />
              <stop offset="100%" stopColor="hsl(var(--primary))" stopOpacity="0.35" />
            </linearGradient>
          </defs>

          {[0, 0.25, 0.5, 0.75, 1].map((t) => {
            const y = padT + plotH * (1 - t)
            return (
              <line
                key={t}
                x1={padL}
                x2={width - padR}
                y1={y}
                y2={y}
                className="stroke-border"
                strokeWidth={1}
              />
            )
          })}

          <text x={4} y={padT + 8} className="fill-muted-foreground text-[10px]">
            Rev
          </text>
          <text x={width - 40} y={padT + 8} className="fill-muted-foreground text-[10px]">
            Orders
          </text>

          {points.map((point, index) => {
            const barW = Math.max(4, slot * 0.55)
            const x = padL + index * slot + (slot - barW) / 2
            const revH = (toMoneyNumber(point.revenueTotal) / maxRevenue) * plotH
            const labelEvery = points.length > 14 ? 7 : points.length > 8 ? 2 : 1
            const showLabel = index % labelEvery === 0 || index === points.length - 1
            return (
              <g key={point.date}>
                <rect
                  x={x}
                  y={padT + plotH - revH}
                  width={barW}
                  height={Math.max(revH, toMoneyNumber(point.revenueTotal) > 0 ? 2 : 0)}
                  fill={`url(#rev-${gradId})`}
                  rx={2}
                  className={hoverIndex === index ? 'opacity-100' : 'opacity-90'}
                />
                {showLabel ? (
                  <text
                    x={padL + index * slot + slot / 2}
                    y={height - 10}
                    textAnchor="middle"
                    className="fill-muted-foreground text-[9px]"
                  >
                    {point.date.slice(5)}
                  </text>
                ) : null}
                <rect
                  x={padL + index * slot}
                  y={padT}
                  width={slot}
                  height={plotH}
                  className="fill-transparent"
                  onMouseEnter={() => setHoverIndex(index)}
                  onMouseLeave={() => setHoverIndex(null)}
                  onFocus={() => setHoverIndex(index)}
                  onBlur={() => setHoverIndex(null)}
                  tabIndex={0}
                  role="img"
                  aria-label={`${point.date}: ${point.placedOrderCount} placed, revenue ${String(point.revenueTotal)} ${currency}`}
                />
              </g>
            )
          })}

          <polyline
            points={linePoints}
            fill="none"
            className="stroke-teal-700"
            strokeWidth={2}
            strokeLinejoin="round"
            strokeLinecap="round"
          />
          {points.map((point, index) => {
            const cx = padL + index * slot + slot / 2
            const cy = padT + plotH - (point.placedOrderCount / maxPlaced) * plotH
            return (
              <circle
                key={`dot-${point.date}`}
                cx={cx}
                cy={cy}
                r={hoverIndex === index ? 4 : 3}
                className="fill-teal-700 stroke-card"
                strokeWidth={1.5}
                pointerEvents="none"
              />
            )
          })}
        </svg>
      </div>

      <details className="rounded-xl border border-border/80 bg-card shadow-sm">
        <summary className="cursor-pointer px-4 py-2.5 text-sm font-medium text-foreground">
          Exact daily values
        </summary>
        <div className="overflow-x-auto border-t border-border">
          <table className="w-full min-w-[28rem] text-left text-sm">
            <caption className="sr-only">Daily timeseries values</caption>
            <thead className="border-b border-border bg-muted/40 text-muted-foreground">
              <tr>
                <th className="px-4 py-2.5 font-medium">Date (UTC)</th>
                <th className="px-4 py-2.5 font-medium">Placed orders</th>
                <th className="px-4 py-2.5 font-medium">Revenue</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {points.map((point) => (
                <tr key={point.date}>
                  <td className="px-4 py-2 font-mono text-xs">{point.date}</td>
                  <td className="px-4 py-2 tabular-nums">{point.placedOrderCount}</td>
                  <td className="px-4 py-2">
                    <ProductPrice
                      unitPrice={point.revenueTotal}
                      currency={currency}
                      className="text-sm font-medium text-foreground"
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  )
}
