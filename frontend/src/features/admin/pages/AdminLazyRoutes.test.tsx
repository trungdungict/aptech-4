import { screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { demoAdminSession, demoCustomerSession, renderWithProviders } from '@/test/render-app'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function mockAdminFetch(
  handler?: (url: string, init?: RequestInit) => Response | Promise<Response>,
) {
  vi.mocked(fetch).mockImplementation(async (input, init) => {
    const url = String(input)
    if (url.includes('/auth/refresh')) {
      return jsonResponse(200, demoAdminSession)
    }
    if (url.includes('/auth/logout')) {
      return new Response(null, { status: 204 })
    }
    if (handler) {
      return handler(url, init)
    }
    return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
  })
}

describe('Admin lazy routes', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('loads the admin overview via a code-split route', async () => {
    mockAdminFetch()
    renderWithProviders(<AppRouter />, { route: '/admin', session: demoAdminSession })

    expect(await screen.findByRole('heading', { name: /admin overview/i })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: /^admin$/i })).toBeInTheDocument()
  })

  it('supports deep links into product create after lazy load', async () => {
    mockAdminFetch((url) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products/new',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /create product/i })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: /^admin$/i })).toBeInTheDocument()
  })

  it('supports deep links into admin order detail', async () => {
    const orderId = 'dddddddd-dddd-dddd-dddd-dddddddddddd'
    mockAdminFetch((url) => {
      if (url.includes(`/admin/orders/${orderId}`)) {
        return jsonResponse(200, {
          id: orderId,
          userId: demoAdminSession.user.id,
          status: 'PAID',
          subtotalAmount: 1000,
          shippingAmount: 0,
          totalAmount: 1000,
          currency: 'VND',
          placedAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
          shipping: {
            recipientName: 'Demo',
            line1: '1 St',
            line2: null,
            city: 'Hanoi',
            stateRegion: null,
            postalCode: null,
            countryCode: 'VN',
            phone: null,
          },
          items: [],
          payment: null,
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: `/admin/orders/${orderId}`,
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /order detail/i })).toBeInTheDocument()
    expect(screen.getByText(orderId)).toBeInTheDocument()
  })

  it('still blocks customers before admin chunks render protected content', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products',
      session: demoCustomerSession,
    })

    expect(await screen.findByRole('heading', { name: /access denied/i })).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.queryByRole('heading', { name: /^products$/i })).not.toBeInTheDocument()
    })
  })

  it('exposes the admin route loading fallback component', async () => {
    const { AdminRouteFallback } = await import(
      '@/features/admin/components/AdminRouteFallback'
    )
    renderWithProviders(<AdminRouteFallback />)
    expect(screen.getByRole('status')).toHaveTextContent(/loading admin/i)
  })
})
