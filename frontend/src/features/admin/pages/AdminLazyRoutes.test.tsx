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

  it('supports deep links into admin promotions hub', async () => {
    mockAdminFetch()
    renderWithProviders(<AppRouter />, {
      route: '/admin/promotions',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /^promotions$/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /open campaigns/i })).toBeInTheDocument()
  })

  it('supports deep links into admin inventory', async () => {
    mockAdminFetch((url) => {
      if (url.includes('/admin/inventory/summary')) {
        return jsonResponse(200, {
          totalProducts: 0,
          inStockCount: 0,
          lowStockCount: 0,
          outOfStockCount: 0,
          lowStockThreshold: 5,
          availableDefinition: 'available = on_hand - reserved',
        })
      }
      if (url.includes('/admin/inventory')) {
        return jsonResponse(200, {
          items: [],
          page: 0,
          size: 10,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/inventory',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /^inventory$/i })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: /^admin$/i })).toBeInTheDocument()
  })

  it('supports deep links into admin analytics', async () => {
    const emptyAnalytics = {
      from: '2026-01-01T00:00:00Z',
      to: '2026-01-31T00:00:00Z',
      timezone: 'UTC',
      currency: 'VND',
      revenueStatuses: ['PAID', 'PROCESSING', 'SHIPPED', 'COMPLETED'],
      placedOrderCount: 0,
      revenueOrderCount: 0,
      revenueTotal: 0,
      averageOrderValue: 0,
      cancellationCount: 0,
      cancellationRate: 0,
      statusCounts: {
        PENDING_PAYMENT: 0,
        PAID: 0,
        PROCESSING: 0,
        SHIPPED: 0,
        COMPLETED: 0,
        CANCELLED: 0,
        FAILED: 0,
      },
      attention: { paid: 0, processing: 0, totalMatching: 0 },
      inventory: {
        totalProducts: 0,
        inStockCount: 0,
        lowStockCount: 0,
        outOfStockCount: 0,
        lowStockThreshold: 5,
        availableDefinition: 'available = on_hand - reserved',
      },
      empty: true,
      revenueDefinition: 'Sum of order.total_amount for statuses PAID/PROCESSING/SHIPPED/COMPLETED',
    }

    mockAdminFetch((url) => {
      if (url.includes('/admin/analytics/overview')) {
        return jsonResponse(200, emptyAnalytics)
      }
      if (url.includes('/admin/analytics/orders/status')) {
        return jsonResponse(200, {
          from: emptyAnalytics.from,
          to: emptyAnalytics.to,
          timezone: 'UTC',
          counts: emptyAnalytics.statusCounts,
          total: 0,
          empty: true,
        })
      }
      if (url.includes('/admin/analytics/orders/timeseries')) {
        return jsonResponse(200, {
          from: emptyAnalytics.from,
          to: emptyAnalytics.to,
          timezone: 'UTC',
          bucket: 'DAY',
          points: [],
          empty: true,
        })
      }
      if (url.includes('/admin/analytics/products/top')) {
        return jsonResponse(200, {
          from: emptyAnalytics.from,
          to: emptyAnalytics.to,
          timezone: 'UTC',
          revenueStatuses: emptyAnalytics.revenueStatuses,
          items: [],
          empty: true,
          note: 'snapshots',
        })
      }
      if (url.includes('/admin/analytics/categories/sales')) {
        return jsonResponse(200, {
          from: emptyAnalytics.from,
          to: emptyAnalytics.to,
          timezone: 'UTC',
          revenueStatuses: emptyAnalytics.revenueStatuses,
          items: [],
          empty: true,
          attributionNote: 'live category',
        })
      }
      if (url.includes('/admin/analytics/promotions/summary')) {
        return jsonResponse(200, {
          from: emptyAnalytics.from,
          to: emptyAnalytics.to,
          timezone: 'UTC',
          currency: 'VND',
          revenueStatuses: emptyAnalytics.revenueStatuses,
          discountTotal: 0,
          ordersWithPromotionCount: 0,
          revenueOrderCount: 0,
          breakdown: [],
          empty: true,
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/analytics',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /^analytics$/i })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: /^admin$/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^analytics$/i })).toBeInTheDocument()
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
          discountAmount: 0,
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
          appliedPromotions: [],
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
