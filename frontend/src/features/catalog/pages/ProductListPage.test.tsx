import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { renderWithProviders } from '@/test/render-app'

const product = {
  id: '22222222-2222-2222-2222-222222222222',
  sku: 'DEMO-ELEC-PHONE',
  name: 'Demo Smartphone X',
  description: 'Flagship demo phone',
  unitPrice: 12990000,
  currency: 'VND',
  categoryId: '11111111-1111-1111-1111-111111111111',
  categoryName: 'Electronics',
  active: true,
  imageUrl: null,
  inStock: true,
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function requestUrl(input: RequestInfo | URL): string {
  if (typeof input === 'string') return input
  if (input instanceof URL) return input.toString()
  if (typeof Request !== 'undefined' && input instanceof Request) return input.url
  return String(input)
}

function mockCatalogApis(options?: { empty?: boolean; failList?: boolean }) {
  vi.mocked(fetch).mockImplementation(async (input) => {
    const url = requestUrl(input)

    if (url.includes('/categories')) {
      return jsonResponse(200, [
        {
          id: '11111111-1111-1111-1111-111111111111',
          name: 'Electronics',
          slug: 'demo-electronics',
          description: null,
          parentId: null,
          active: true,
        },
      ])
    }
    if (url.includes('/products?') || url.endsWith('/products')) {
      if (options?.failList) {
        return jsonResponse(500, {
          status: 500,
          error: 'Server Error',
          message: 'Catalog unavailable',
          path: '/api/v1/products',
          details: [],
        })
      }
      return jsonResponse(200, {
        items: options?.empty ? [] : [product],
        page: 0,
        size: 20,
        totalElements: options?.empty ? 0 : 1,
        totalPages: options?.empty ? 0 : 1,
        first: true,
        last: true,
      })
    }
    return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
  })
}

describe('ProductListPage', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('renders products from the API', async () => {
    mockCatalogApis()
    renderWithProviders(<AppRouter />, { route: '/products' })

    expect(await screen.findByRole('heading', { name: /demo smartphone x/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /view demo smartphone x/i })).toBeInTheDocument()
  })


  it('shows an empty state when no products match', async () => {
    mockCatalogApis({ empty: true })
    renderWithProviders(<AppRouter />, { route: '/products' })

    expect(await screen.findByText(/no products match these filters/i)).toBeInTheDocument()
  })

  it('shows an error state with retry', async () => {
    mockCatalogApis({ failList: true })
    const user = userEvent.setup()
    renderWithProviders(<AppRouter />, { route: '/products' })

    expect(await screen.findByRole('alert')).toHaveTextContent(/catalog unavailable/i)
    mockCatalogApis()
    await user.click(screen.getByRole('button', { name: /retry/i }))
    expect(await screen.findByRole('heading', { name: /demo smartphone x/i })).toBeInTheDocument()
  })

  it('updates sort and resets page in the request URL', async () => {
    mockCatalogApis()
    const user = userEvent.setup()
    renderWithProviders(<AppRouter />, { route: '/products?page=2' })

    await screen.findByRole('heading', { name: /demo smartphone x/i })
    await user.selectOptions(screen.getByLabelText(/^sort$/i), 'price,desc')

    await waitFor(() => {
      const productCalls = vi
        .mocked(fetch)
        .mock.calls.map((call) => String(call[0]))
        .filter((url) => url.includes('/products'))
      expect(productCalls.some((url) => url.includes('sort=price%2Cdesc'))).toBe(true)
      expect(productCalls.at(-1)).not.toContain('page=2')
    })
  })

  it('clears filters back to defaults', async () => {
    mockCatalogApis()
    const user = userEvent.setup()
    renderWithProviders(<AppRouter />, {
      route: '/products?q=phone&inStock=true&sort=price,asc',
    })

    await screen.findByRole('heading', { name: /demo smartphone x/i })
    const filters = screen.getByLabelText(/catalog filters/i)
    await user.click(within(filters).getByRole('button', { name: /clear filters/i }))

    await waitFor(() => {
      expect(screen.getByLabelText(/^search$/i)).toHaveValue('')
      expect(screen.getByLabelText(/^availability$/i)).toHaveValue('')
      expect(screen.getByLabelText(/^sort$/i)).toHaveValue('name,asc')
    })
  })
})
