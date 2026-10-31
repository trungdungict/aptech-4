import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

describe('LoginPage', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('shows required-field validation', async () => {
    const user = userEvent.setup()
    renderWithProviders(<AppRouter />, { route: '/login' })

    await user.click(await screen.findByRole('button', { name: /sign in/i }))

    expect(await screen.findByText(/email is required/i)).toBeInTheDocument()
    expect(screen.getByText(/password is required/i)).toBeInTheDocument()
    expect(
      vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes('/auth/login')),
    ).toBe(false)
  })

  it('shows backend invalid credentials and keeps the form enabled after failure', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/login')) {
        return jsonResponse(401, {
          status: 401,
          error: 'Unauthorized',
          message: 'Invalid email or password',
          path: '/api/v1/auth/login',
          details: [],
        })
      }
      if (url.includes('/categories')) {
        return jsonResponse(200, [])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/login' })

    await user.type(await screen.findByLabelText(/^email$/i), 'user@example.com')
    await user.type(screen.getByLabelText(/^password$/i), 'wrong-password')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid email or password/i)
    expect(screen.getByRole('button', { name: /sign in/i })).toBeEnabled()
  })

  it('logs in successfully and updates auth state', async () => {
    const user = userEvent.setup()
    const authBody = {
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        id: '11111111-1111-1111-1111-111111111111',
        email: 'customer@example.com',
        fullName: 'Demo Customer',
        roles: ['CUSTOMER'],
      },
    }
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/login')) {
        return jsonResponse(200, authBody)
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
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/login' })

    await user.type(await screen.findByLabelText(/^email$/i), 'customer@example.com')
    await user.type(screen.getByLabelText(/^password$/i), 'CustomerDemo_change_me!')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(
        screen.getByRole('heading', {
          name: /stock your kitchen from the live aptech-4 catalog/i,
        }),
      ).toBeInTheDocument()
    })
    expect(screen.getAllByText(/demo customer/i).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument()
  })

  it('disables submit while the login request is pending', async () => {
    const user = userEvent.setup()
    let resolveFetch: ((value: Response) => void) | undefined
    vi.mocked(fetch).mockImplementation(
      () =>
        new Promise<Response>((resolve) => {
          resolveFetch = resolve
        }),
    )

    renderWithProviders(<AppRouter />, { route: '/login' })
    await user.type(await screen.findByLabelText(/^email$/i), 'customer@example.com')
    await user.type(screen.getByLabelText(/^password$/i), 'CustomerDemo_change_me!')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByRole('button', { name: /signing in/i })).toBeDisabled()

    resolveFetch?.(
      jsonResponse(200, {
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: {
          id: '11111111-1111-1111-1111-111111111111',
          email: 'customer@example.com',
          fullName: 'Demo Customer',
          roles: ['CUSTOMER'],
        },
      }),
    )

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument()
    })
  })
})
