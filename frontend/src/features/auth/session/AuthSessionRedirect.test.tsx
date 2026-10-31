import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearPersistedAuth } from '@/features/auth/session/token-store'
import { clearSession, getAccessToken } from '@/features/auth/session/session-coordinator'
import {
  demoAdminSession,
  demoCustomerSession,
  renderWithProviders,
} from '@/test/render-app'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('auth session redirect on protected routes', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('loads a protected admin route with a valid session', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoAdminSession)
      }
      if (url.includes('/admin/orders')) {
        return jsonResponse(200, {
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
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

    expect(await screen.findByRole('heading', { name: /orders/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /sign in/i })).not.toBeInTheDocument()
  })

  it('redirects to login when an authenticated API call returns 401 after refresh failure', async () => {
    let refreshCalls = 0
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        refreshCalls += 1
        // Boot restore succeeds once; subsequent refresh after expired access fails.
        if (refreshCalls === 1) {
          return jsonResponse(200, demoAdminSession)
        }
        return jsonResponse(401, {
          status: 401,
          error: 'Unauthorized',
          message: 'Invalid refresh token',
        })
      }
      if (url.includes('/admin/orders')) {
        return jsonResponse(401, {
          status: 401,
          error: 'Unauthorized',
          message: 'Access token expired',
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/orders',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument()
    expect(screen.queryByText(/request failed with status 401/i)).not.toBeInTheDocument()
    expect(getAccessToken()).toBeNull()
    expect(refreshCalls).toBeGreaterThanOrEqual(2)
  })

  it('redirects to login when storage is cleared before visiting /admin/orders', async () => {
    clearPersistedAuth()
    clearSession()

    vi.mocked(fetch).mockImplementation(async () =>
      jsonResponse(401, { status: 401, error: 'Unauthorized', message: 'no session' }),
    )

    renderWithProviders(<AppRouter />, { route: '/admin/orders' })

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /^orders$/i })).not.toBeInTheDocument()
    expect(screen.queryByText(/request failed with status 401/i)).not.toBeInTheDocument()
  })

  it('returns to the original internal route after login via next=', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/login')) {
        return jsonResponse(200, demoAdminSession)
      }
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoAdminSession)
      }
      if (url.includes('/admin/orders')) {
        return jsonResponse(200, {
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/login?next=%2Fadmin%2Forders%3Fstatus%3DPAID',
    })

    await user.type(await screen.findByLabelText(/^email$/i), 'admin@example.com')
    await user.type(screen.getByLabelText(/^password$/i), 'AdminDemo_change_me!')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByRole('heading', { name: /orders/i })).toBeInTheDocument()
  })

  it('falls back safely when next= is an external URL', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/login')) {
        return jsonResponse(200, demoCustomerSession)
      }
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      if (url.includes('/categories')) {
        return jsonResponse(200, [])
      }
      if (url.includes('/products')) {
        return jsonResponse(200, {
          items: [],
          page: 0,
          size: 8,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        })
      }
      if (url.includes('/cart')) {
        return jsonResponse(200, {
          id: 'cart-1',
          items: [],
          subtotal: '0',
          currency: 'VND',
          hasUnavailableItems: false,
        })
      }
      if (url.includes('/promotions/homepage')) {
        return jsonResponse(200, { hero: [], promoTop: null, promoBottom: null })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/login?next=https%3A%2F%2Fevil.example%2Fphish',
    })

    await user.type(await screen.findByLabelText(/^email$/i), 'customer@example.com')
    await user.type(screen.getByLabelText(/^password$/i), 'CustomerDemo_change_me!')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(
      await screen.findByRole('heading', {
        name: /stock your kitchen from the live aptech-4 catalog/i,
      }),
    ).toBeInTheDocument()
  })

  it('keeps the session on 403 and shows access denied for CUSTOMER on admin', async () => {
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
    expect(screen.queryByRole('heading', { name: /sign in/i })).not.toBeInTheDocument()
    expect(getAccessToken()).toBeTruthy()
  })

  it('does not clear the session on 5xx from a protected admin API', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoAdminSession)
      }
      if (url.includes('/admin/orders')) {
        return jsonResponse(503, {
          status: 503,
          error: 'Service Unavailable',
          message: 'Temporarily unavailable',
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/orders',
      session: demoAdminSession,
    })

    expect(await screen.findByText(/temporarily unavailable|request failed with status 503/i)).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /sign in/i })).not.toBeInTheDocument()
    expect(getAccessToken()).toBeTruthy()
  })

  it('handles multiple simultaneous 401s without leaving a stuck authenticated admin shell', async () => {
    let refreshCalls = 0
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        refreshCalls += 1
        return jsonResponse(401, {
          status: 401,
          error: 'Unauthorized',
          message: 'Invalid refresh token',
        })
      }
      return jsonResponse(401, {
        status: 401,
        error: 'Unauthorized',
        message: 'Access token expired',
      })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/orders',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument()
    expect(screen.queryByText(/request failed with status 401/i)).not.toBeInTheDocument()
    // Boot restore may refresh once; list query may refresh once more — both single-flighted per wave.
    expect(refreshCalls).toBeLessThanOrEqual(2)
    expect(getAccessToken()).toBeNull()
  })

  it('retries the original request after a successful refresh', async () => {
    let orderCalls = 0
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, {
          ...demoAdminSession,
          accessToken: 'rotated-access',
          refreshToken: 'rotated-refresh',
        })
      }
      if (url.includes('/admin/orders')) {
        orderCalls += 1
        if (orderCalls === 1) {
          return jsonResponse(401, {
            status: 401,
            error: 'Unauthorized',
            message: 'Access token expired',
          })
        }
        return jsonResponse(200, {
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
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

    expect(await screen.findByRole('heading', { name: /orders/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /sign in/i })).not.toBeInTheDocument()
    expect(orderCalls).toBeGreaterThanOrEqual(2)
  })
})
