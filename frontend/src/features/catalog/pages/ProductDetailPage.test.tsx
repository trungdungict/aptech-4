import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { renderWithProviders } from '@/test/render-app'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('ProductDetailPage', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('renders product details for a valid id', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(200, {
        id: '22222222-2222-2222-2222-222222222222',
        sku: 'DEMO-ELEC-PHONE',
        name: 'Demo Smartphone X',
        description: 'Flagship demo phone',
        unitPrice: 12990000,
        currency: 'VND',
        categoryId: '11111111-1111-1111-1111-111111111111',
        categoryName: 'Electronics',
        active: true,
        imageUrl: null,
        inStock: true,
      }),
    )

    renderWithProviders(<AppRouter />, {
      route: '/products/22222222-2222-2222-2222-222222222222',
    })

    expect(await screen.findByRole('heading', { name: /demo smartphone x/i })).toBeInTheDocument()

    expect(screen.getByText(/flagship demo phone/i)).toBeInTheDocument()
    expect(screen.getByText(/demo-elec-phone/i)).toBeInTheDocument()
  })

  it('shows unavailable state for 404 responses', async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(404, {
        status: 404,
        error: 'Not Found',
        message: 'Product not found',
        path: '/api/v1/products/22222222-2222-2222-2222-222222222222',
        details: [],
      }),
    )

    renderWithProviders(<AppRouter />, {
      route: '/products/22222222-2222-2222-2222-222222222222',
    })

    expect(await screen.findByRole('heading', { name: /product unavailable/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /back to catalog/i })).toBeInTheDocument()
  })

  it('handles invalid route identifiers without calling the API', async () => {
    renderWithProviders(<AppRouter />, { route: '/products/not-a-uuid' })

    expect(await screen.findByRole('heading', { name: /invalid product link/i })).toBeInTheDocument()
    expect(
      vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes('/products/')),
    ).toBe(false)
  })
})
