import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { OrderPriceSummary } from './OrderPriceSummary'

describe('OrderPriceSummary', () => {
  it('hides discount row for legacy zero-discount orders', () => {
    render(
      <OrderPriceSummary
        currency="VND"
        subtotalAmount={100000}
        shippingAmount={0}
        discountAmount={0}
        totalAmount={100000}
        appliedPromotions={[]}
      />,
    )

    expect(screen.getByText(/^subtotal$/i)).toBeInTheDocument()
    expect(screen.getByText(/^shipping$/i)).toBeInTheDocument()
    expect(screen.getByText(/^total$/i)).toBeInTheDocument()
    expect(screen.queryByText(/^discount$/i)).not.toBeInTheDocument()
  })

  it('renders backend discount and multiple applied promotions as provided', () => {
    render(
      <OrderPriceSummary
        currency="VND"
        subtotalAmount={200000}
        shippingAmount={0}
        discountAmount={40000}
        totalAmount={160000}
        appliedPromotions={[
          {
            campaignId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
            campaignName: 'Category Deal',
            strategyType: 'CATEGORY_DISCOUNT',
            discountAmount: 25000,
          },
          {
            campaignId: null,
            campaignName: 'Stack Bonus',
            strategyType: 'FIXED_AMOUNT_DISCOUNT',
            discountAmount: 15000,
          },
        ]}
      />,
    )

    expect(screen.getByText(/^discount$/i)).toBeInTheDocument()
    expect(screen.getByText(/category deal/i)).toBeInTheDocument()
    expect(screen.getByText(/stack bonus/i)).toBeInTheDocument()
    expect(screen.getByText(/category_discount/i)).toBeInTheDocument()
    expect(screen.getByText(/fixed_amount_discount/i)).toBeInTheDocument()
  })
})
