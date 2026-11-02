import { screen, waitFor, within } from '@testing-library/react'
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

const summary = {
  totalProducts: 3,
  inStockCount: 1,
  lowStockCount: 1,
  outOfStockCount: 1,
  lowStockThreshold: 5,
  availableDefinition: 'available = on_hand - reserved',
}

const rowInStock = {
  productId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  sku: 'SKU-OK',
  name: 'Plenty Tea',
  active: true,
  categoryId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
  categoryName: 'Drinks',
  hasInventoryRow: true,
  quantityOnHand: 20,
  quantityReserved: 2,
  available: 18,
  updatedAt: '2026-01-01T00:00:00Z',
}

const rowLow = {
  ...rowInStock,
  productId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  sku: 'SKU-LOW',
  name: 'Low Juice',
  quantityOnHand: 4,
  quantityReserved: 1,
  available: 3,
}

const rowOut = {
  ...rowInStock,
  productId: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
  sku: 'SKU-OUT',
  name: 'Missing Milk',
  hasInventoryRow: false,
  quantityOnHand: 0,
  quantityReserved: 0,
  available: 0,
  updatedAt: null,
}

function pageResponse(
  items: Array<typeof rowInStock | typeof rowLow | typeof rowOut>,
  page = 0,
  totalElements = items.length,
) {
  const size = 10
  const totalPages = Math.max(1, Math.ceil(totalElements / size))
  return {
    items,
    page,
    size,
    totalElements,
    totalPages,
    first: page === 0,
    last: page >= totalPages - 1,
  }
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

describe('Admin inventory page', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('blocks CUSTOMER from inventory admin content', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      return jsonResponse(403, { status: 403, error: 'Forbidden', message: 'Forbidden' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/inventory',
      session: demoCustomerSession,
    })

    expect(await screen.findByRole('heading', { name: /access denied/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /^inventory$/i })).not.toBeInTheDocument()
  })

  it('renders summary cards and inventory rows from the admin APIs', async () => {
    mockAdminFetch((url) => {
      if (url.includes('/admin/inventory/summary')) {
        return jsonResponse(200, summary)
      }
      if (url.includes('/admin/inventory')) {
        return jsonResponse(200, pageResponse([rowInStock, rowLow, rowOut]))
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/inventory',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /^inventory$/i })).toBeInTheDocument()
    expect(await screen.findByLabelText('Total products: 3')).toBeInTheDocument()
    expect(screen.getByLabelText('In stock: 1')).toBeInTheDocument()
    expect(screen.getByLabelText('Low stock: 1')).toBeInTheDocument()
    expect(screen.getByLabelText('Out of stock: 1')).toBeInTheDocument()
    expect(screen.getByText(/low-stock threshold is configured on the server/i)).toBeInTheDocument()
    expect(screen.getByText('Plenty Tea')).toBeInTheDocument()
    expect(screen.getByText('SKU-OK')).toBeInTheDocument()
    expect(screen.getByText('Low Juice')).toBeInTheDocument()
    expect(screen.getByText(/no inventory row yet/i)).toBeInTheDocument()
    expect(screen.getAllByText('In stock').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Low stock').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Out of stock').length).toBeGreaterThan(0)
  })

  it('applies search and stock filters via URL query params', async () => {
    const user = userEvent.setup()
    const requests: string[] = []

    mockAdminFetch((url) => {
      if (url.includes('/admin/inventory/summary')) {
        return jsonResponse(200, summary)
      }
      if (url.includes('/admin/inventory')) {
        requests.push(url)
        return jsonResponse(200, pageResponse([rowLow]))
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/inventory',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /^inventory$/i })).toBeInTheDocument()

    await user.clear(screen.getByLabelText(/^search$/i))
    await user.type(screen.getByLabelText(/^search$/i), 'Juice')
    await user.selectOptions(screen.getByLabelText(/^stock status$/i), 'LOW')
    await user.selectOptions(screen.getByLabelText(/^product status$/i), 'true')
    await user.click(screen.getByRole('button', { name: /^apply$/i }))

    await waitFor(() => {
      expect(requests.some((url) => url.includes('q=Juice'))).toBe(true)
      expect(requests.some((url) => url.includes('stockStatus=LOW'))).toBe(true)
      expect(requests.some((url) => url.includes('active=true'))).toBe(true)
    })
  })

  it('paginates with shared Pagination and preserves size=10', async () => {
    const user = userEvent.setup()
    const items = Array.from({ length: 10 }, (_, i) => ({
      ...rowInStock,
      productId: `11111111-1111-4111-8111-1111111111${i.toString(16).padStart(2, '0')}`,
      name: `Item ${i + 1}`,
      sku: `SKU-${i + 1}`,
    }))
    const requests: string[] = []

    mockAdminFetch((url) => {
      if (url.includes('/admin/inventory/summary')) {
        return jsonResponse(200, summary)
      }
      if (url.includes('/admin/inventory')) {
        requests.push(url)
        const page = Number(new URL(url, 'http://local').searchParams.get('page') ?? '0')
        if (page === 0) {
          return jsonResponse(200, {
            ...pageResponse(items, 0, 11),
            totalPages: 2,
            last: false,
          })
        }
        return jsonResponse(
          200,
          pageResponse(
            [
              {
                ...rowInStock,
                productId: '11111111-1111-4111-8111-111111111199',
                name: 'Item 11',
                sku: 'SKU-11',
              },
            ],
            1,
            11,
          ),
        )
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/inventory',
      session: demoAdminSession,
    })

    expect(await screen.findByText('Item 1')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Pagination' })).toBeInTheDocument()
    expect(screen.getByText(/page 1 of 2 · 11 products/i)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByText('Item 11')).toBeInTheDocument()
    expect(requests.some((url) => url.includes('page=1'))).toBe(true)
    expect(requests.some((url) => url.includes('size=10'))).toBe(true)
  })

  it('shows empty and error/retry states for the list', async () => {
    const user = userEvent.setup()
    let failList = true

    mockAdminFetch((url) => {
      if (url.includes('/admin/inventory/summary')) {
        return jsonResponse(200, summary)
      }
      if (url.includes('/admin/inventory')) {
        if (failList) {
          return jsonResponse(500, {
            status: 500,
            error: 'Error',
            message: 'Inventory boom',
          })
        }
        return jsonResponse(200, pageResponse([]))
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/inventory',
      session: demoAdminSession,
    })

    expect(await screen.findByText(/inventory boom/i)).toBeInTheDocument()
    failList = false
    await user.click(screen.getByRole('button', { name: /^retry$/i }))
    expect(
      await screen.findByText(/no products match these inventory filters/i),
    ).toBeInTheDocument()
  })

  it('updates on-hand successfully and refreshes list/summary without editing reserved', async () => {
    const user = userEvent.setup()
    let putBodies: unknown[] = []
    let listVersion = 0

    mockAdminFetch((url, init) => {
      if (url.includes('/admin/inventory/summary')) {
        return jsonResponse(200, {
          ...summary,
          lowStockCount: listVersion > 0 ? 0 : 1,
          inStockCount: listVersion > 0 ? 2 : 1,
        })
      }
      if (url.includes(`/admin/products/${rowLow.productId}/inventory`)) {
        if (init?.method === 'PUT') {
          putBodies.push(JSON.parse(String(init.body)))
          return jsonResponse(200, {
            productId: rowLow.productId,
            quantityOnHand: 12,
            quantityReserved: 1,
            available: 11,
            updatedAt: '2026-01-02T00:00:00Z',
          })
        }
        return jsonResponse(200, {
          productId: rowLow.productId,
          quantityOnHand: 4,
          quantityReserved: 1,
          available: 3,
          updatedAt: '2026-01-01T00:00:00Z',
        })
      }
      if (url.includes('/admin/inventory')) {
        if (listVersion === 0) {
          return jsonResponse(200, pageResponse([rowLow]))
        }
        return jsonResponse(
          200,
          pageResponse([
            {
              ...rowLow,
              quantityOnHand: 12,
              available: 11,
            },
          ]),
        )
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/inventory',
      session: demoAdminSession,
    })

    expect(await screen.findByText('Low Juice')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /set on hand/i }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/reserved units cannot be edited/i)).toBeInTheDocument()
    expect(within(dialog).getByTestId('adjust-reserved-display')).toHaveTextContent('1')
    expect(within(dialog).queryByLabelText(/reserved/i)).not.toBeInTheDocument()

    const input = within(dialog).getByLabelText(/quantity on hand/i)
    await user.clear(input)
    await user.type(input, '12')
    listVersion = 1
    await user.click(within(dialog).getByRole('button', { name: /update on hand/i }))

    expect(await within(dialog).findByText(/on-hand quantity updated/i)).toBeInTheDocument()
    expect(putBodies).toEqual([{ quantityOnHand: 12 }])
    await waitFor(() => {
      expect(screen.getByText('12')).toBeInTheDocument()
    })
  })

  it('surfaces validation API errors and prevents duplicate submits while pending', async () => {
    const user = userEvent.setup()
    let putCount = 0
    const putGate: { resolve: ((value: Response) => void) | null } = { resolve: null }

    mockAdminFetch((url, init) => {
      if (url.includes('/admin/inventory/summary')) {
        return jsonResponse(200, summary)
      }
      if (url.includes(`/admin/products/${rowLow.productId}/inventory`)) {
        if (init?.method === 'PUT') {
          putCount += 1
          return new Promise<Response>((resolve) => {
            putGate.resolve = resolve
          })
        }
        return jsonResponse(200, {
          productId: rowLow.productId,
          quantityOnHand: 4,
          quantityReserved: 1,
          available: 3,
          updatedAt: '2026-01-01T00:00:00Z',
        })
      }
      if (url.includes('/admin/inventory')) {
        return jsonResponse(200, pageResponse([rowLow]))
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/inventory',
      session: demoAdminSession,
    })

    expect(await screen.findByText('Low Juice')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /set on hand/i }))
    const dialog = await screen.findByRole('dialog')
    const input = within(dialog).getByLabelText(/quantity on hand/i)
    await user.clear(input)
    await user.type(input, '0')

    const submit = within(dialog).getByRole('button', { name: /update on hand/i })
    await user.click(submit)
    expect(await within(dialog).findByRole('button', { name: /saving/i })).toBeDisabled()
    await user.click(submit)
    expect(putCount).toBe(1)

    putGate.resolve?.(
      jsonResponse(400, {
        status: 400,
        error: 'Bad Request',
        message: 'quantityOnHand (0) cannot be less than quantityReserved (1)',
      }),
    )

    expect(
      await within(dialog).findByText(/cannot be less than quantityReserved/i),
    ).toBeInTheDocument()
  })
})
