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

const category = {
  id: '11111111-1111-4111-8111-111111111111',
  name: 'Electronics',
  slug: 'electronics',
  description: 'Devices',
  parentId: null,
  active: true,
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

describe('Admin category pages', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('blocks CUSTOMER from category admin content', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      return jsonResponse(403, { status: 403, error: 'Forbidden', message: 'Forbidden' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/categories',
      session: demoCustomerSession,
    })

    expect(await screen.findByRole('heading', { name: /access denied/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /^categories$/i })).not.toBeInTheDocument()
  })

  it('renders category list from the admin API', async () => {
    mockAdminFetch((url) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/categories',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /^categories$/i })).toBeInTheDocument()
    expect(await screen.findByText('Electronics')).toBeInTheDocument()
    expect(screen.getByText('electronics')).toBeInTheDocument()
  })

  it('shows empty category state', async () => {
    mockAdminFetch(() => jsonResponse(200, []))

    renderWithProviders(<AppRouter />, {
      route: '/admin/categories',
      session: demoAdminSession,
    })

    expect(await screen.findByText(/no categories yet/i)).toBeInTheDocument()
  })

  it('creates a category with the supported payload and handles duplicate conflict', async () => {
    const user = userEvent.setup()
    const creates: unknown[] = []
    let failOnce = true

    mockAdminFetch((url, init) => {
      if (url.endsWith('/admin/categories') && init?.method === 'POST') {
        creates.push(JSON.parse(String(init.body)))
        if (failOnce) {
          failOnce = false
          return jsonResponse(409, {
            status: 409,
            error: 'Conflict',
            message: 'Category name already exists',
          })
        }
        return jsonResponse(201, category)
      }
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/categories/new',
      session: demoAdminSession,
    })

    await screen.findByRole('heading', { name: /create category/i })
    await user.click(screen.getByRole('button', { name: /create category/i }))
    expect(await screen.findByText(/name is required/i)).toBeInTheDocument()

    await user.type(screen.getByLabelText(/^name$/i), 'Electronics')
    await user.click(screen.getByRole('button', { name: /create category/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/already exists/i)
    expect(screen.getByLabelText(/^name$/i)).toHaveValue('Electronics')

    await user.click(screen.getByRole('button', { name: /create category/i }))
    await waitFor(() => expect(creates.length).toBe(2))
    expect(creates[0]).toEqual({
      name: 'Electronics',
      description: null,
      parentId: null,
    })
    expect(await screen.findByRole('heading', { name: /edit category/i })).toBeInTheDocument()
  })

  it('soft-disables a category through the status endpoint', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    let current = { ...category }
    const bodies: unknown[] = []

    mockAdminFetch((url, init) => {
      if (url.includes('/status') && init?.method === 'PATCH') {
        bodies.push(JSON.parse(String(init.body)))
        current = { ...current, active: false }
        return jsonResponse(200, current)
      }
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [current])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/categories',
      session: demoAdminSession,
    })

    await screen.findByText('Electronics')
    await user.click(screen.getByRole('button', { name: /deactivate/i }))
    await waitFor(() => expect(bodies).toEqual([{ active: false }]))
    expect(await screen.findByRole('button', { name: /activate/i })).toBeInTheDocument()
  })
})
