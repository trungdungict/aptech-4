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
    expect(fetch).not.toHaveBeenCalled()
  })

  it('shows duplicate email errors from the API', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(409, {
        status: 409,
        error: 'Conflict',
        message: 'Email already registered',
        path: '/api/v1/auth/register',
        details: [],
      }),
    )

    renderWithProviders(<AppRouter />, { route: '/register' })
    await user.type(await screen.findByLabelText(/full name/i), 'Ada Lovelace')
    await user.type(screen.getByLabelText(/^email$/i), 'ada@example.com')
    await user.type(screen.getByLabelText(/^password$/i), 'Password1!')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/email already registered/i)

    const body = JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body)) as Record<string, unknown>
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
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse(201, {
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
      }),
    )

    renderWithProviders(<AppRouter />, { route: '/register' })
    await user.type(await screen.findByLabelText(/full name/i), 'Ada Lovelace')
    await user.type(screen.getByLabelText(/^email$/i), 'ada@example.com')
    await user.type(screen.getByLabelText(/^password$/i), 'Password1!')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(screen.getByText(/ada@example.com \(CUSTOMER\)/i)).toBeInTheDocument()
    })
    expect(screen.getByRole('button', { name: /sign out/i })).toBeInTheDocument()
  })
})

