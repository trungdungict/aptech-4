import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

const orderId = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
const userId = '11111111-1111-4111-8111-111111111111'

const orderDetail = {
  id: orderId,
  userId,
  status: 'PAID',
  subtotalAmount: 12990000,
  shippingAmount: 0,
  totalAmount: 12990000,
  currency: 'VND',
  placedAt: '2026-01-01T12:00:00Z',
  updatedAt: '2026-01-01T12:30:00Z',
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
      productId: '22222222-2222-4222-8222-222222222222',
      productSku: 'DEMO-ELEC-PHONE',
      productName: 'Demo Smartphone X',
      unitPrice: 12990000,
      quantity: 1,
      lineTotal: 12990000,
    },
  ],
  payment: {
    paymentId: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    provider: 'MOCK',
    status: 'SUCCEEDED',
    amount: 12990000,
    currency: 'VND',
    providerReference: 'mock-ref',
    failureReason: null,
  },
}

function mockAdminFetch(
  handler: (url: string, init?: RequestInit) => Response | Promise<Response>,
) {
  vi.mocked(fetch).mockImplementation(async (input, init) => {
    const url = String(input)
    if (url.includes('/auth/refresh')) {
      return jsonResponse(200, demoAdminSession)
    }
    return handler(url, init)
  })
}

describe('Admin order pages', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('blocks CUSTOMER from admin order content', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      return jsonResponse(403, { status: 403, error: 'Forbidden', message: 'Forbidden' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/orders',
      session: demoCustomerSession,
    })

    expect(await screen.findByRole('heading', { name: /access denied/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /^orders$/i })).not.toBeInTheDocument()
  })

  it('renders the admin order list from API data', async () => {
    mockAdminFetch((url) => {
      if (url.includes('/admin/orders')) {
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

    renderWithProviders(<AppRouter />, {
      route: '/admin/orders',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /^orders$/i })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: orderId })).toBeInTheDocument()
    expect(screen.getByText(userId)).toBeInTheDocument()
    expect(screen.getByText(/1 total/i)).toBeInTheDocument()
    expect(screen.queryByText(/revenue/i)).not.toBeInTheDocument()
  })

  it('shows empty and error states', async () => {
    mockAdminFetch(() =>
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

    renderWithProviders(<AppRouter />, {
      route: '/admin/orders',
      session: demoAdminSession,
    })
    expect(await screen.findByText(/no orders match/i)).toBeInTheDocument()
  })

  it('sends supported status filter and sort parameters', async () => {
    const user = userEvent.setup()
    const urls: string[] = []

    mockAdminFetch((url) => {
      urls.push(url)
      return jsonResponse(200, {
        items: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
        first: true,
        last: true,
      })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/orders',
      session: demoAdminSession,
    })

    await screen.findByRole('heading', { name: /^orders$/i })
    await user.selectOptions(screen.getByLabelText(/status filter/i), 'PAID')
    await user.selectOptions(screen.getByLabelText(/^sort$/i), 'totalAmount,desc')
    await user.click(screen.getByRole('button', { name: /apply/i }))

    await waitFor(() => {
      expect(
        urls.some(
          (url) =>
            url.includes('status=PAID') &&
            url.includes('sort=totalAmount%2Cdesc'),
        ),
      ).toBe(true)
    })
  })

  it('renders detail snapshots and supported fulfillment actions', async () => {
    mockAdminFetch((url) => {
      if (url.includes(`/admin/orders/${orderId}`)) {
        return jsonResponse(200, orderDetail)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: `/admin/orders/${orderId}`,
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /order detail/i })).toBeInTheDocument()
    expect(screen.getByText(/price snapshots/i)).toBeInTheDocument()
    expect(screen.getByText(/demo smartphone x/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /set processing/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /set cancelled/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /set paid/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /refund/i })).not.toBeInTheDocument()
  })

  it('updates status through the admin PATCH endpoint and refreshes', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let current = { ...orderDetail }
    const bodies: unknown[] = []

    mockAdminFetch((url, init) => {
      if (url.includes('/status') && init?.method === 'PATCH') {
        bodies.push(JSON.parse(String(init.body)))
        current = { ...current, status: 'PROCESSING' }
        return jsonResponse(200, current)
      }
      if (url.includes(`/admin/orders/${orderId}`)) {
        return jsonResponse(200, current)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: `/admin/orders/${orderId}`,
      session: demoAdminSession,
    })

    await screen.findByRole('button', { name: /set processing/i })
    await user.click(screen.getByRole('button', { name: /set processing/i }))

    await waitFor(() => expect(bodies).toEqual([{ status: 'PROCESSING' }]))
    expect(await screen.findByText(/status updated to processing/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /set shipped/i })).toBeInTheDocument()
  })

  it('shows conflict errors without inventing a new status', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    mockAdminFetch((url, init) => {
      if (url.includes('/status') && init?.method === 'PATCH') {
        return jsonResponse(409, {
          status: 409,
          error: 'Conflict',
          message: 'Invalid status transition: PAID → COMPLETED',
        })
      }
      if (url.includes(`/admin/orders/${orderId}`)) {
        return jsonResponse(200, orderDetail)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: `/admin/orders/${orderId}`,
      session: demoAdminSession,
    })

    await user.click(await screen.findByRole('button', { name: /set processing/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid status transition/i)
    expect(screen.getByText('PAID')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /set processing/i })).toBeInTheDocument()
  })

  it('handles inaccessible order details', async () => {
    mockAdminFetch(() =>
      jsonResponse(404, {
        status: 404,
        error: 'Not Found',
        message: 'Order not found',
      }),
    )

    renderWithProviders(<AppRouter />, {
      route: '/admin/orders/ffffffff-ffff-4fff-8fff-ffffffffffff',
      session: demoAdminSession,
    })

    expect(await screen.findByText(/order unavailable/i)).toBeInTheDocument()
  })
})
