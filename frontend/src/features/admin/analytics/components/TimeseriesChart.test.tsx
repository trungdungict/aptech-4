import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { TimeseriesChart } from './TimeseriesChart'

describe('TimeseriesChart', () => {
  it('keeps zero-filled days and sparse activity readable with exact table values', () => {
    render(
      <TimeseriesChart
        currency="VND"
        empty={false}
        points={[
          { date: '2026-05-01', placedOrderCount: 2, revenueTotal: 100 },
          { date: '2026-05-02', placedOrderCount: 0, revenueTotal: 0 },
          { date: '2026-05-03', placedOrderCount: 0, revenueTotal: 0 },
        ]}
      />,
    )

    expect(screen.getByText(/1\/3 days with activity/i)).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /daily revenue bars and placed-order line/i })).toBeInTheDocument()
    expect(screen.getByText('2026-05-01')).toBeInTheDocument()
    expect(screen.getByText('2026-05-02')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
  })

  it('shows empty copy for zero-filled empty ranges', () => {
    render(
      <TimeseriesChart
        currency="VND"
        empty
        points={[{ date: '2088-01-01', placedOrderCount: 0, revenueTotal: 0 }]}
      />,
    )
    expect(screen.getByText(/Daily values are zero-filled/i)).toBeInTheDocument()
  })
})
