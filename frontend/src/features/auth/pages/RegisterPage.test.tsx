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

describe('RegisterPage', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('validates required fields and password length', async () => {
    const user = userEvent.setup()
    renderWithProviders(<AppRouter />, { route: '/register' })

    await user.type(await screen.findByLabelText(/full name/i), 'Ada')
    await user.type(screen.getByLabelText(/^email$/i), 'ada@example.com')
    await user.type(screen.getByLabelText(/^password$/i), 'short')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByText(/password must be at least 8 characters/i)).toBeInTheDocument()
    expect(
      vi.mocked(fetch).mock.calls.some(([url]) => String(url).includes('/auth/register')),
    ).toBe(false)
  })

  it('shows duplicate email errors from the API', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/register')) {
        return jsonResponse(409, {
          status: 409,
          error: 'Conflict',
          message: 'Email already registered',
          path: '/api/v1/auth/register',
          details: [],
        })
      }
      if (url.includes('/categories')) {
        return jsonResponse(200, [])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/register' })
    await user.type(await screen.findByLabelText(/full name/i), 'Ada Lovelace')
    await user.type(screen.getByLabelText(/^email$/i), 'ada@example.com')
    await user.type(screen.getByLabelText(/^password$/i), 'Password1!')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/email already registered/i)

    const registerCall = vi
      .mocked(fetch)
      .mock.calls.find(([url]) => String(url).includes('/auth/register'))
    expect(registerCall).toBeDefined()
    const body = JSON.parse(String(registerCall?.[1]?.body)) as Record<string, unknown>
    expect(body).toEqual({
      email: 'ada@example.com',
      password: 'Password1!',
      fullName: 'Ada Lovelace',
    })
    expect(body).not.toHaveProperty('roles')
    expect(body).not.toHaveProperty('role')
  })

  it('registers successfully and authenticates from the 201 AuthResponse', async () => {
    const user = userEvent.setup()
    const authBody = {
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: {
        id: '22222222-2222-2222-2222-222222222222',
        email: 'ada@example.com',
        fullName: 'Ada Lovelace',
        roles: ['CUSTOMER'],
      },
    }
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/register')) {
        return jsonResponse(201, authBody)
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

    renderWithProviders(<AppRouter />, { route: '/register' })
    await user.type(await screen.findByLabelText(/full name/i), 'Ada Lovelace')
    await user.type(screen.getByLabelText(/^email$/i), 'ada@example.com')
    await user.type(screen.getByLabelText(/^password$/i), 'Password1!')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(
        screen.getByRole('heading', {
          name: /stock your kitchen from the live aptech-4 catalog/i,
        }),
      ).toBeInTheDocument()
    })
    expect(screen.getAllByText(/ada lovelace/i).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument()
  })
})

