import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { InventorySnapshot } from './InventorySnapshot'

describe('InventorySnapshot', () => {
  it('renders distribution counts, percentages, threshold, and inventory link', () => {
    render(
      <MemoryRouter>
        <InventorySnapshot
          inventory={{
            totalProducts: 10,
            inStockCount: 7,
            lowStockCount: 2,
            outOfStockCount: 1,
            lowStockThreshold: 5,
            availableDefinition: 'available = on_hand - reserved',
          }}
        />
      </MemoryRouter>,
    )

    expect(screen.getByText(/Threshold ≤ 5 available/i)).toBeInTheDocument()
    expect(screen.getByText('10')).toBeInTheDocument()
    expect(screen.getByText('(70.0%)')).toBeInTheDocument()
    expect(screen.getByText('(20.0%)')).toBeInTheDocument()
    expect(screen.getByText('(10.0%)')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /view inventory/i })).toHaveAttribute(
      'href',
      '/admin/inventory',
    )
  })
})
