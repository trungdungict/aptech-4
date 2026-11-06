import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { changeAppLocale, i18n } from '@/lib/i18n'
import { demoCustomerSession, renderWithProviders } from '@/test/render-app'

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

  it('still adds to cart from the product detail page for authenticated customers', async () => {
    const user = userEvent.setup()
    const cartPosts: unknown[] = []
    const productId = '22222222-2222-2222-2222-222222222222'

    vi.mocked(fetch).mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      if (url.includes(`/products/${productId}`) && !url.includes('/cart')) {
        return jsonResponse(200, {
          id: productId,
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
        })
      }
      if (url.includes('/cart/items') && init?.method === 'POST') {
        cartPosts.push(JSON.parse(String(init.body)))
        return jsonResponse(200, {
          id: 'cart-1',
          items: [],
          subtotal: 0,
          currency: 'VND',
          hasUnavailableItems: false,
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: `/products/${productId}`,
      session: demoCustomerSession,
    })

    expect(await screen.findByRole('heading', { name: /demo smartphone x/i })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /increase quantity/i }))
    await user.click(screen.getByRole('button', { name: /^add to cart$/i }))

    await waitFor(() => {
      expect(cartPosts).toEqual([{ productId, quantity: 2 }])
    })
    expect(await screen.findByText(/added to cart/i)).toBeInTheDocument()
  })

  it('renders Vietnamese invalid-link state after switching locale', async () => {
    await changeAppLocale('vi')
    renderWithProviders(<AppRouter />, { route: '/products/not-a-uuid' })

    expect(
      await screen.findByRole('heading', { name: i18n.t('catalog:detail.invalidLink') }),
    ).toBeInTheDocument()
    expect(
      screen.getAllByRole('link', { name: i18n.t('catalog:detail.backToCatalog') }).length,
    ).toBeGreaterThan(0)
  })
})
