import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import type { Product } from '../types'
import { ProductCard } from './ProductCard'

const baseProduct: Product = {
  id: '11111111-1111-1111-1111-111111111111',
  sku: 'FOOD-FRUIT-BANANA-001',
  name: 'Yellow Bananas',
  description: null,
  unitPrice: '25000',
  currency: 'VND',
  categoryId: '22222222-2222-2222-2222-222222222222',
  categoryName: 'Fruits & Vegetables',
  active: true,
  imageUrl: null,
  inStock: true,
}

describe('ProductCard', () => {
  it('renders grocery card with resolved FoodMart thumbnail', () => {
    render(
      <MemoryRouter>
        <ProductCard product={baseProduct} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: /view yellow bananas/i })).toHaveAttribute(
      'href',
      `/products/${baseProduct.id}`,
    )
    expect(screen.getByText('Fruits & Vegetables')).toBeInTheDocument()
    const img = document.querySelector('img[src*="thumb-bananas.png"]')
    expect(img).not.toBeNull()
    expect(img).toHaveAttribute('loading', 'lazy')
  })

  it('shows a neutral placeholder when no SKU or name match exists', () => {
    render(
      <MemoryRouter>
        <ProductCard
          product={{
            ...baseProduct,
            sku: 'UNKNOWN-SKU',
            name: 'Mystery Widget',
            imageUrl: null,
          }}
        />
      </MemoryRouter>,
    )

    expect(screen.getByText(/no image/i)).toBeInTheDocument()
  })
})
