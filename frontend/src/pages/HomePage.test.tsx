import { screen } from '@testing-library/react'
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

describe('storefront home', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input) => {
        const url = String(input)
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
        return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
      }),
    )
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
  })

  it('renders the grocery landing headline, catalog CTA, and auth entry points', async () => {
    renderWithProviders(<AppRouter />, { route: '/' })

    expect(
      await screen.findByRole('heading', {
        name: /stock your kitchen from the live aptech-4 catalog/i,
      }),
    ).toBeInTheDocument()
    const catalogLinks = screen.getAllByRole('link', { name: /browse catalog/i })
    expect(catalogLinks.length).toBeGreaterThan(0)
    expect(catalogLinks.some((link) => link.getAttribute('href') === '/products')).toBe(true)
    expect(screen.getAllByRole('link', { name: /sign in/i }).length).toBeGreaterThan(0)
    expect(screen.getByText(/templatesjungle/i)).toBeInTheDocument()
    expect(screen.getByText(/themewagon/i)).toBeInTheDocument()
    expect(screen.queryByText(/api base url/i)).not.toBeInTheDocument()
  })

  it('exposes FoodMart-style section landmarks', async () => {
    renderWithProviders(<AppRouter />, { route: '/' })

    expect(await screen.findByRole('heading', { name: /grocery categories/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /trending products/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /best-selling products/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /just arrived/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /why shop here/i })).toBeInTheDocument()
  })
})
