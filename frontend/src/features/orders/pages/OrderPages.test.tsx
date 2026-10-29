import { screen, waitFor } from '@testing-library/react'
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

const orderId = 'dddddddd-dddd-dddd-dddd-dddddddddddd'

const orderDetail = {
  id: orderId,
  userId: demoCustomerSession.user.id,
  status: 'PAID',
  subtotalAmount: 12990000,
  shippingAmount: 0,
  totalAmount: 12990000,
  currency: 'VND',
  placedAt: '2026-01-01T12:00:00Z',
  updatedAt: '2026-01-01T12:00:00Z',
  shipping: {
    recipientName: 'Demo Customer',
    line1: '1 Demo Street',
    line2: null,
    city: 'Hanoi',
    stateRegion: null,
    postalCode: '10000',
    countryCode: 'VN',
    phone: null,
  },
  items: [
    {
      productId: '22222222-2222-2222-2222-222222222222',
      productSku: 'DEMO-ELEC-PHONE',
      productName: 'Demo Smartphone X',
      unitPrice: 12990000,
      quantity: 1,
      lineTotal: 12990000,
    },
  ],
  payment: {
    paymentId: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    provider: 'MOCK',
    status: 'SUCCEEDED',
    amount: 12990000,
    currency: 'VND',
    providerReference: 'mock-ref',
    failureReason: null,
  },
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

describe('Order pages', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('renders order history from API data', async () => {
    mockFetchRouter((url) => {
      if (url.includes('/api/v1/orders')) {
        return jsonResponse(200, {
          items: [orderDetail],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/orders', session: demoCustomerSession })

    expect(await screen.findByRole('heading', { name: /^orders$/i })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: new RegExp(orderId, 'i') })).toBeInTheDocument()
    expect(screen.getByText(/1 total/i)).toBeInTheDocument()
  })

  it('renders empty order history', async () => {
    mockFetchRouter(() =>
      jsonResponse(200, {
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        first: true,
        last: true,
      }),
    )

    renderWithProviders(<AppRouter />, { route: '/orders', session: demoCustomerSession })
    expect(await screen.findByText(/no orders yet/i)).toBeInTheDocument()
  })

  it('shows order detail snapshots and handles inaccessible orders', async () => {
    mockFetchRouter((url) => {
      if (url.includes(`/orders/${orderId}`)) {
        return jsonResponse(200, orderDetail)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'Order not found' })
    })

    renderWithProviders(<AppRouter />, {
      route: `/orders/${orderId}`,
      session: demoCustomerSession,
    })

    expect(await screen.findByRole('heading', { name: /order detail/i })).toBeInTheDocument()
    expect(screen.getByText(/price snapshots/i)).toBeInTheDocument()
    expect(screen.getByText(/demo smartphone x/i)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /payment \(mock\)/i })).toBeInTheDocument()
  })

  it('handles inaccessible order without leaking details', async () => {
    mockFetchRouter(() =>
      jsonResponse(404, {
        status: 404,
        error: 'Not Found',
        message: 'Order not found',
        details: [],
      }),
    )

    renderWithProviders(<AppRouter />, {
      route: '/orders/ffffffff-ffff-ffff-ffff-ffffffffffff',
      session: demoCustomerSession,
    })

    expect(await screen.findByText(/order unavailable/i)).toBeInTheDocument()
    expect(screen.queryByText(/stack/i)).not.toBeInTheDocument()
  })

  it('clears private order cache presentation after logout', async () => {
    mockFetchRouter((url) => {
      if (url.includes('/orders')) {
        return jsonResponse(200, {
          items: [orderDetail],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        })
      }
      if (url.includes('/auth/logout')) {
        return new Response(null, { status: 204 })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/orders', session: demoCustomerSession })
    expect(await screen.findByText(orderId)).toBeInTheDocument()

    // Sign out via header
    const signOut = screen.getByRole('button', { name: /sign out/i })
    signOut.click()

    await waitFor(() => {
      expect(screen.queryByText(orderId)).not.toBeInTheDocument()
    })
  })
})
