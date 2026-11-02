import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { StatusBars } from './StatusBars'

describe('StatusBars', () => {
  it('shows counts and percentages of total placed orders', () => {
    render(
      <StatusBars
        counts={{ PAID: 2, CANCELLED: 1, PENDING_PAYMENT: 1, PROCESSING: 0, SHIPPED: 0, COMPLETED: 0, FAILED: 0 }}
        total={4}
        empty={false}
      />,
    )

    expect(screen.getByText(/Total placed/i)).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText('(50.0%)')).toBeInTheDocument()
    expect(screen.getAllByText('(25.0%)').length).toBe(2)
    expect(screen.getAllByText('(0.0%)').length).toBeGreaterThan(0)
  })

  it('renders empty notice while keeping zero statuses', () => {
    render(
      <StatusBars
        counts={{ PAID: 0, CANCELLED: 0 }}
        total={0}
        empty
      />,
    )
    expect(screen.getByText(/All statuses are zero/i)).toBeInTheDocument()
  })
})
