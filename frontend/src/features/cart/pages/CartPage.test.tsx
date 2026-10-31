import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { demoCustomerSession, renderWithProviders } from '@/test/render-app'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const cartFixture = {
  id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  items: [
    {
      id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      productId: '22222222-2222-2222-2222-222222222222',
      productName: 'Demo Smartphone X',
      imageUrl: null,
      quantity: 2,
      unitPrice: 12990000,
      lineSubtotal: 25980000,
      currency: 'VND',
      available: true,
      productActive: true,
    },
  ],
  subtotal: 25980000,
  currency: 'VND',
  hasUnavailableItems: false,
}

const emptyCart = {
  id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  items: [],
  subtotal: 0,
  currency: 'VND',
  hasUnavailableItems: false,
}

function mockFetchRouter(
  handler: (url: string, init?: RequestInit) => Response | Promise<Response>,
) {
  vi.mocked(fetch).mockImplementation(async (input, init) => {
    const url = String(input)
    if (url.includes('/auth/refresh')) {
      return jsonResponse(200, demoCustomerSession)
    }
    return handler(url, init)
  })
}

describe('CartPage', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('redirects unauthenticated users to login with next', async () => {
    renderWithProviders(<AppRouter />, { route: '/cart' })
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument()
    })
  })

  it('renders cart lines from the API', async () => {
    mockFetchRouter((url) => {
      if (url.endsWith('/cart') || url.includes('/api/v1/cart')) {
        return jsonResponse(200, cartFixture)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/cart', session: demoCustomerSession })

    expect(await screen.findByRole('heading', { name: /^cart$/i })).toBeInTheDocument()
    expect(await screen.findByText(/demo smartphone x/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /checkout/i })).toBeInTheDocument()
  })

  it('prefers uploaded product imageUrl over FoodMart name fallback', async () => {
    const uploaded = '/uploads/products/9d1f7e4b-1111-4111-8111-111111111111.png'
    mockFetchRouter((url) => {
      if (url.endsWith('/cart') || url.includes('/api/v1/cart')) {
        return jsonResponse(200, {
          ...cartFixture,
          items: [
            {
              ...cartFixture.items[0],
              productName: 'Fresh Banana Bundle',
              imageUrl: uploaded,
            },
          ],
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/cart', session: demoCustomerSession })
    await screen.findByText(/fresh banana bundle/i)
    const img = document.querySelector(`img[src="${uploaded}"]`)
    expect(img).not.toBeNull()
  })

  it('renders empty cart state', async () => {
    mockFetchRouter(() => jsonResponse(200, emptyCart))

    renderWithProviders(<AppRouter />, { route: '/cart', session: demoCustomerSession })

    expect(await screen.findByText(/your cart is empty/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /browse catalog/i })).toBeInTheDocument()
  })

  it('updates quantity via PATCH and shows failure without inventing success', async () => {
    mockFetchRouter((url, init) => {
      if (init?.method === 'PATCH' && url.includes('/cart/items/')) {
        return jsonResponse(400, {
          status: 400,
          error: 'Bad Request',
          message: 'Product unavailable',
          details: [],
        })
      }
      if (url.includes('/cart')) {
        return jsonResponse(200, cartFixture)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/cart', session: demoCustomerSession })
    const qty = await screen.findByLabelText(/quantity for demo smartphone x/i)
    expect(qty).toHaveValue(2)

    fireEvent.change(qty, { target: { value: '3' } })

    expect(await screen.findByRole('alert')).toHaveTextContent(/product unavailable/i)
    expect(screen.getByLabelText(/quantity for demo smartphone x/i)).toHaveValue(2)
  })

  it('calls DELETE on remove and refreshes cart from response', async () => {
    const user = userEvent.setup()

    mockFetchRouter((url, init) => {
      if (init?.method === 'DELETE' && url.includes('/cart/items/bbbbbbbb')) {
        return jsonResponse(200, emptyCart)
      }
      if (url.includes('/cart')) {
        return jsonResponse(200, cartFixture)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/cart', session: demoCustomerSession })
    await screen.findByText(/demo smartphone x/i)
    await user.click(screen.getByRole('button', { name: /remove/i }))

    expect(await screen.findByText(/your cart is empty/i)).toBeInTheDocument()
    expect(vi.mocked(fetch).mock.calls.some(([input, init]) => {
      return String(input).includes('/cart/items/bbbbbbbb') && init?.method === 'DELETE'
    })).toBe(true)
  })
})
