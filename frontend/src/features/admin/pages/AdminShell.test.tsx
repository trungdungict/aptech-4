import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { safeInternalPath } from '@/lib/safe-redirect'
import {
  demoAdminSession,
  demoCustomerSession,
  demoUnknownRoleSession,
  renderWithProviders,
} from '@/test/render-app'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function mockSessionRefresh(session: typeof demoAdminSession) {
  vi.mocked(fetch).mockImplementation(async (input) => {
    const url = String(input)
    if (url.includes('/auth/refresh')) {
      return jsonResponse(200, session)
    }
    if (url.includes('/auth/logout')) {
      return new Response(null, { status: 204 })
    }
    return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
  })
}

describe('Admin shell + role gate', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('redirects unauthenticated users to login with a safe next path', async () => {
    renderWithProviders(<AppRouter />, { route: '/admin' })

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument()
    expect(safeInternalPath('/admin')).toBe('/admin')
    expect(safeInternalPath('https://evil.example/admin')).toBe('/')
  })

  it('denies CUSTOMER access without rendering the admin shell', async () => {
    mockSessionRefresh(demoCustomerSession)
    renderWithProviders(<AppRouter />, { route: '/admin', session: demoCustomerSession })

    expect(await screen.findByRole('heading', { name: /access denied/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /admin overview/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: /^admin$/i })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /back to storefront/i })).toBeInTheDocument()
  })

  it('fails closed when roles are missing', async () => {
    mockSessionRefresh(demoUnknownRoleSession)
    renderWithProviders(<AppRouter />, { route: '/admin', session: demoUnknownRoleSession })

    expect(await screen.findByRole('heading', { name: /access denied/i })).toBeInTheDocument()
    expect(screen.queryByText(/admin workspace/i)).not.toBeInTheDocument()
  })

  it('allows ADMIN to see the scaffold landing and shell', async () => {
    mockSessionRefresh(demoAdminSession)
    renderWithProviders(<AppRouter />, { route: '/admin', session: demoAdminSession })

    expect(await screen.findByRole('heading', { name: /admin overview/i })).toBeInTheDocument()
    expect(screen.getAllByText(/scaffold/i).length).toBeGreaterThan(0)
    expect(screen.getByRole('navigation', { name: /^admin$/i })).toBeInTheDocument()
    expect(screen.getByText(/demo admin/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^overview$/i })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: /^orders$/i })).toHaveAttribute('href', '/admin/orders')
    expect(screen.queryByText(/revenue/i)).not.toBeInTheDocument()
  })

  it('exposes product, category, and orders nav', async () => {
    mockSessionRefresh(demoAdminSession)
    renderWithProviders(<AppRouter />, { route: '/admin', session: demoAdminSession })

    await screen.findByRole('heading', { name: /admin overview/i })
    expect(screen.getByRole('link', { name: /^products$/i })).toHaveAttribute(
      'href',
      '/admin/products',
    )
    expect(screen.getByRole('link', { name: /^categories$/i })).toHaveAttribute(
      'href',
      '/admin/categories',
    )
    expect(screen.getByRole('link', { name: /^orders$/i })).toHaveAttribute(
      'href',
      '/admin/orders',
    )
  })

  it('navigates to the storefront from the admin shell', async () => {
    const user = userEvent.setup()
    mockSessionRefresh(demoAdminSession)
    renderWithProviders(<AppRouter />, { route: '/admin', session: demoAdminSession })

    await screen.findByRole('heading', { name: /admin overview/i })
    await user.click(screen.getByRole('link', { name: /^storefront$/i }))

    expect(
      await screen.findByRole('heading', {
        name: /stock your kitchen from the live aptech-4 catalog/i,
      }),
    ).toBeInTheDocument()
  })

  it('logs out via the existing auth flow and leaves the admin area', async () => {
    const user = userEvent.setup()
    mockSessionRefresh(demoAdminSession)
    renderWithProviders(<AppRouter />, { route: '/admin', session: demoAdminSession })

    await screen.findByRole('heading', { name: /admin overview/i })
    await user.click(screen.getByRole('button', { name: /sign out/i }))

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument()
    expect(
      vi.mocked(fetch).mock.calls.some(([input]) => String(input).includes('/auth/logout')),
    ).toBe(true)
  })

  it('revokes admin UI when a later session is CUSTOMER-only', async () => {
    mockSessionRefresh(demoAdminSession)
    const first = renderWithProviders(<AppRouter />, {
      route: '/admin',
      session: demoAdminSession,
    })
    expect(await screen.findByRole('heading', { name: /admin overview/i })).toBeInTheDocument()
    first.unmount()

    mockSessionRefresh(demoCustomerSession)
    renderWithProviders(<AppRouter />, { route: '/admin', session: demoCustomerSession })
    expect(await screen.findByRole('heading', { name: /access denied/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /admin overview/i })).not.toBeInTheDocument()
  })

  it('does not flash admin content while the session is still booting', async () => {
    // No session seed → booting then anonymous; never show overview.
    renderWithProviders(<AppRouter />, { route: '/admin' })

    await waitFor(() => {
      expect(screen.queryByRole('heading', { name: /admin overview/i })).not.toBeInTheDocument()
    })
    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument()
  })
})
