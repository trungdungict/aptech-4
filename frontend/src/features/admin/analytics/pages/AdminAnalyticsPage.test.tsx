import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { demoAdminSession, demoCustomerSession, renderWithProviders } from '@/test/render-app'

import { adminAnalyticsQueryKeys } from '../hooks/admin-analytics-query-keys'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const inventoryEmbed = {
  totalProducts: 12,
  inStockCount: 8,
  lowStockCount: 3,
  outOfStockCount: 1,
  lowStockThreshold: 5,
  availableDefinition: 'available = on_hand - reserved',
}

const overviewBody = {
  from: '2026-05-01T00:00:00Z',
  to: '2026-05-31T00:00:00Z',
  timezone: 'UTC',
  currency: 'VND',
  revenueStatuses: ['PAID', 'PROCESSING', 'SHIPPED', 'COMPLETED'],
  placedOrderCount: 4,
  revenueOrderCount: 2,
  revenueTotal: 270,
  averageOrderValue: 135,
  cancellationCount: 1,
  cancellationRate: 0.25,
  statusCounts: {
    PENDING_PAYMENT: 1,
    PAID: 1,
    PROCESSING: 0,
    SHIPPED: 0,
    COMPLETED: 1,
    CANCELLED: 1,
    FAILED: 0,
  },
  attention: { paid: 2, processing: 1, totalMatching: 3 },
  inventory: inventoryEmbed,
  empty: false,
  revenueDefinition: 'Sum of order.total_amount for statuses PAID/PROCESSING/SHIPPED/COMPLETED',
}

const emptyOverview = {
  ...overviewBody,
  from: '2088-01-01T00:00:00Z',
  to: '2088-01-10T00:00:00Z',
  placedOrderCount: 0,
  revenueOrderCount: 0,
  revenueTotal: 0,
  averageOrderValue: 0,
  cancellationCount: 0,
  cancellationRate: 0,
  empty: true,
  attention: { paid: 0, processing: 0, totalMatching: 0 },
  inventory: { ...inventoryEmbed, totalProducts: 0, inStockCount: 0, lowStockCount: 0, outOfStockCount: 0 },
}

const statusBody = {
  from: overviewBody.from,
  to: overviewBody.to,
  timezone: 'UTC',
  counts: overviewBody.statusCounts,
  total: 4,
  empty: false,
}

const timeseriesBody = {
  from: overviewBody.from,
  to: overviewBody.to,
  timezone: 'UTC',
  bucket: 'DAY',
  points: [
    { date: '2026-05-01', placedOrderCount: 2, revenueTotal: 100 },
    { date: '2026-05-02', placedOrderCount: 0, revenueTotal: 0 },
  ],
  empty: false,
}

const topBody = {
  from: overviewBody.from,
  to: overviewBody.to,
  timezone: 'UTC',
  revenueStatuses: overviewBody.revenueStatuses,
  items: [
    {
      productSku: 'SKU-A',
      productName: 'Alpha Tea',
      productId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      unitsSold: 3,
      revenue: 150,
      path: '/admin/products/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/edit',
    },
    {
      productSku: 'SKU-B',
      productName: 'Orphan Juice',
      productId: null,
      unitsSold: 1,
      revenue: 50,
      path: null,
    },
  ],
  empty: false,
  note: 'Quantities and revenue use historical order_item snapshots, not current catalog prices.',
}

const categoryBody = {
  from: overviewBody.from,
  to: overviewBody.to,
  timezone: 'UTC',
  revenueStatuses: overviewBody.revenueStatuses,
  items: [
    {
      categoryId: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
      categoryName: 'Drinks',
      unitsSold: 4,
      revenue: 220,
      orderLineCount: 3,
    },
  ],
  empty: false,
  attributionNote:
    'Attribution uses live products.category_id. Historical category is not preserved; lines with null product_id are excluded.',
}

const promotionsBody = {
  from: overviewBody.from,
  to: overviewBody.to,
  timezone: 'UTC',
  currency: 'VND',
  revenueStatuses: overviewBody.revenueStatuses,
  discountTotal: 15,
  ordersWithPromotionCount: 1,
  revenueOrderCount: 2,
  breakdown: [
    {
      campaignSlug: 'camp-gone',
      strategyCode: '',
      campaignName: 'Deleted Camp',
      strategyName: 'Percent Off',
      strategyType: 'PERCENTAGE_DISCOUNT',
      discountTotal: 15,
      orderCount: 1,
    },
  ],
  empty: false,
}

function rangeFromUrl(url: string): { from?: string; to?: string } {
  const queryIndex = url.indexOf('?')
  if (queryIndex < 0) return {}
  const params = new URLSearchParams(url.slice(queryIndex))
  return {
    from: params.get('from') ?? undefined,
    to: params.get('to') ?? undefined,
  }
}

function withRequestedRange<T extends { from: string; to: string }>(
  body: T,
  url: string,
): T {
  const range = rangeFromUrl(url)
  if (!range.from && !range.to) {
    return body
  }
  return {
    ...body,
    from: range.from ?? body.from,
    to: range.to ?? body.to,
  }
}

function mockAnalyticsFetch(options?: {
  overview?: unknown
  status?: unknown | 'error'
  timeseries?: unknown
  top?: unknown
  categories?: unknown
  promotions?: unknown
  onRequest?: (url: string) => void
}) {
  vi.mocked(fetch).mockImplementation(async (input) => {
    const url = String(input)
    if (url.includes('/auth/refresh')) {
      return jsonResponse(200, demoAdminSession)
    }
    options?.onRequest?.(url)

    if (url.includes('/admin/analytics/overview')) {
      const body = options?.overview ?? overviewBody
      return jsonResponse(200, withRequestedRange(body as typeof overviewBody, url))
    }
    if (url.includes('/admin/analytics/orders/status')) {
      if (options?.status === 'error') {
        return jsonResponse(500, { status: 500, error: 'Error', message: 'status failed' })
      }
      const body = options?.status ?? statusBody
      return jsonResponse(200, withRequestedRange(body as typeof statusBody, url))
    }
    if (url.includes('/admin/analytics/orders/timeseries')) {
      const body = options?.timeseries ?? timeseriesBody
      return jsonResponse(200, withRequestedRange(body as typeof timeseriesBody, url))
    }
    if (url.includes('/admin/analytics/products/top')) {
      const body = options?.top ?? topBody
      return jsonResponse(200, withRequestedRange(body as typeof topBody, url))
    }
    if (url.includes('/admin/analytics/categories/sales')) {
      const body = options?.categories ?? categoryBody
      return jsonResponse(200, withRequestedRange(body as typeof categoryBody, url))
    }
    if (url.includes('/admin/analytics/promotions/summary')) {
      const body = options?.promotions ?? promotionsBody
      return jsonResponse(200, withRequestedRange(body as typeof promotionsBody, url))
    }
    if (url.includes('/admin/inventory/summary')) {
      throw new Error('inventory summary must not be called from analytics page')
    }
    return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
  })
}

describe('Admin analytics page', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('blocks CUSTOMER from analytics admin content', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      return jsonResponse(403, { status: 403, error: 'Forbidden', message: 'Forbidden' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/analytics',
      session: demoCustomerSession,
    })

    expect(await screen.findByRole('heading', { name: /access denied/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /^analytics$/i })).not.toBeInTheDocument()
  })

  it('loads analytics via lazy route and shows nav link', async () => {
    const requested: string[] = []
    mockAnalyticsFetch({
      onRequest: (url) => {
        if (url.includes('/admin/analytics')) requested.push(url)
      },
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/analytics',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /^analytics$/i })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: /^admin$/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /^analytics$/i })).toHaveAttribute(
      'href',
      '/admin/analytics',
    )

    const firstWave = requested.filter((url) => !url.includes('from='))
    expect(firstWave.some((url) => url.includes('/admin/analytics/overview'))).toBe(true)
    expect(firstWave.every((url) => !url.includes('from=') && !url.includes('to='))).toBe(true)

    // Wait through URL sync + refetch so KPI cards stay mounted with API values
    await waitFor(() => {
      expect(screen.getByLabelText(/from \(utc day\)/i)).toHaveValue('2026-05-01')
      expect(screen.getByLabelText(/through \(utc day/i)).toHaveValue('2026-05-30')
      expect(screen.getByText(/Attention \(all dates\)/i)).toBeInTheDocument()
      expect(screen.getByText(/135/)).toBeInTheDocument()
    })
    expect(screen.getByText(/Inventory snapshot/i)).toBeInTheDocument()
    expect(screen.getByText('Catalog unlinked')).toBeInTheDocument()
    expect(screen.getAllByText(/Historical category is not preserved/i).length).toBeGreaterThan(0)
    expect(screen.getByText('Deleted Camp')).toBeInTheDocument()
    expect(screen.getAllByText('—').length).toBeGreaterThan(0)

    await waitFor(() => {
      expect(requested.some((url) => url.includes('from=2026-05-01T00%3A00%3A00Z'))).toBe(true)
    })
  })

  it('omits from/to on initial requests then applies exclusive UTC Instants', async () => {
    const user = userEvent.setup()
    const requested: string[] = []
    mockAnalyticsFetch({
      onRequest: (url) => {
        if (url.includes('/admin/analytics')) requested.push(url)
      },
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/analytics',
      session: demoAdminSession,
    })

    await screen.findByRole('heading', { name: /^analytics$/i })
    await waitFor(() => {
      expect(screen.getByLabelText(/from \(utc day\)/i)).toHaveValue('2026-05-01')
    })

    requested.length = 0
    fireEvent.change(screen.getByLabelText(/from \(utc day\)/i), {
      target: { value: '2026-06-01' },
    })
    fireEvent.change(screen.getByLabelText(/through \(utc day/i), {
      target: { value: '2026-06-04' },
    })
    await user.click(screen.getByRole('button', { name: /^apply$/i }))

    await waitFor(() => {
      expect(requested.some((url) => url.includes('from=2026-06-01T00%3A00%3A00.000Z'))).toBe(true)
      expect(requested.some((url) => url.includes('to=2026-06-05T00%3A00%3A00.000Z'))).toBe(true)
    })
  })

  it('keeps overview KPIs when status section fails and allows retry', async () => {
    const user = userEvent.setup()
    let statusFails = true
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoAdminSession)
      }
      if (url.includes('/admin/analytics/overview')) {
        return jsonResponse(200, overviewBody)
      }
      if (url.includes('/admin/analytics/orders/status')) {
        if (statusFails) {
          return jsonResponse(500, { status: 500, error: 'Error', message: 'status failed' })
        }
        return jsonResponse(200, statusBody)
      }
      if (url.includes('/admin/analytics/orders/timeseries')) {
        return jsonResponse(200, timeseriesBody)
      }
      if (url.includes('/admin/analytics/products/top')) {
        return jsonResponse(200, topBody)
      }
      if (url.includes('/admin/analytics/categories/sales')) {
        return jsonResponse(200, categoryBody)
      }
      if (url.includes('/admin/analytics/promotions/summary')) {
        return jsonResponse(200, promotionsBody)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/analytics',
      session: demoAdminSession,
    })

    await waitFor(() => {
      expect(screen.getByText(/Attention \(all dates\)/i)).toBeInTheDocument()
      expect(screen.getByText(/270/)).toBeInTheDocument()
    })

    const statusHeading = screen.getByRole('heading', { name: /^order status$/i })
    const statusSection = statusHeading.closest('[aria-labelledby]')
    expect(statusSection).toBeTruthy()
    const statusRetry = await within(statusSection as HTMLElement).findByRole('button', {
      name: /retry/i,
    })

    statusFails = false
    await user.click(statusRetry)
    await waitFor(() => {
      expect(screen.getByText(/^Total placed:/i)).toBeInTheDocument()
    })
  })

  it('renders empty/zero states without inventing product rows', async () => {
    mockAnalyticsFetch({
      overview: emptyOverview,
      status: {
        from: emptyOverview.from,
        to: emptyOverview.to,
        timezone: 'UTC',
        counts: {
          PENDING_PAYMENT: 0,
          PAID: 0,
          PROCESSING: 0,
          SHIPPED: 0,
          COMPLETED: 0,
          CANCELLED: 0,
          FAILED: 0,
        },
        total: 0,
        empty: true,
      },
      timeseries: {
        from: emptyOverview.from,
        to: emptyOverview.to,
        timezone: 'UTC',
        bucket: 'DAY',
        points: [{ date: '2088-01-01', placedOrderCount: 0, revenueTotal: 0 }],
        empty: true,
      },
      top: { ...topBody, items: [], empty: true },
      categories: { ...categoryBody, items: [], empty: true },
      promotions: { ...promotionsBody, breakdown: [], empty: true, discountTotal: 0, ordersWithPromotionCount: 0 },
    })

    // Seed URL with the empty window so overview URL-sync does not remount queries mid-assert
    // (that double-fetch race flaked under full-suite load).
    renderWithProviders(<AppRouter />, {
      route: `/admin/analytics?from=${encodeURIComponent(emptyOverview.from)}&to=${encodeURIComponent(emptyOverview.to)}`,
      session: demoAdminSession,
    })

    expect(
      await screen.findByText(/No orders in this range\. KPI values are zero/i),
    ).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByText(/No product sales in this range/i)).toBeInTheDocument()
      expect(screen.getByText(/No category sales in this range/i)).toBeInTheDocument()
    })
    expect(screen.queryByText('Alpha Tea')).not.toBeInTheDocument()
    expect(screen.queryByText('SKU-A')).not.toBeInTheDocument()
  })

  it('builds query keys with range and limit', () => {
    expect(adminAnalyticsQueryKeys.overview({})).toEqual(['admin', 'analytics', 'overview', {}])
    expect(
      adminAnalyticsQueryKeys.topProducts({ from: 'a', to: 'b' }, 5),
    ).toEqual(['admin', 'analytics', 'topProducts', { from: 'a', to: 'b' }, 5])
  })

  it('surfaces client-side inverted range validation before Apply', async () => {
    const user = userEvent.setup()
    mockAnalyticsFetch()

    renderWithProviders(<AppRouter />, {
      route: '/admin/analytics?from=2026-05-01T00:00:00Z&to=2026-05-31T00:00:00Z',
      session: demoAdminSession,
    })

    await screen.findByRole('heading', { name: /^analytics$/i })
    fireEvent.change(screen.getByLabelText(/from \(utc day\)/i), {
      target: { value: '2026-06-10' },
    })
    fireEvent.change(screen.getByLabelText(/through \(utc day/i), {
      target: { value: '2026-06-01' },
    })
    await user.click(screen.getByRole('button', { name: /^apply$/i }))

    expect(await screen.findByText(/on or before/i)).toBeInTheDocument()
  })

  it('refresh refetches all sections, preserves range/limit, and shows loading then success', async () => {
    const user = userEvent.setup()
    const requested: string[] = []
    const overviewGate: { release: (() => void) | null } = { release: null }
    let gateOverview = false

    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoAdminSession)
      }
      if (url.includes('/admin/analytics')) {
        requested.push(url)
      }
      if (gateOverview && url.includes('/admin/analytics/overview')) {
        await new Promise<void>((resolve) => {
          overviewGate.release = resolve
        })
      }
      if (url.includes('/admin/analytics/overview')) {
        return jsonResponse(200, withRequestedRange(overviewBody, url))
      }
      if (url.includes('/admin/analytics/orders/status')) {
        return jsonResponse(200, withRequestedRange(statusBody, url))
      }
      if (url.includes('/admin/analytics/orders/timeseries')) {
        return jsonResponse(200, withRequestedRange(timeseriesBody, url))
      }
      if (url.includes('/admin/analytics/products/top')) {
        return jsonResponse(200, withRequestedRange(topBody, url))
      }
      if (url.includes('/admin/analytics/categories/sales')) {
        return jsonResponse(200, withRequestedRange(categoryBody, url))
      }
      if (url.includes('/admin/analytics/promotions/summary')) {
        return jsonResponse(200, withRequestedRange(promotionsBody, url))
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route:
        '/admin/analytics?from=2026-05-01T00:00:00.000Z&to=2026-05-31T00:00:00.000Z&limit=5',
      session: demoAdminSession,
    })

    await waitFor(() => {
      expect(screen.getByText(/Attention \(all dates\)/i)).toBeInTheDocument()
    })
    expect(screen.getByLabelText(/top \/ category limit/i)).toHaveValue('5')

    requested.length = 0
    gateOverview = true
    await user.click(screen.getByRole('button', { name: /^refresh$/i }))

    expect(await screen.findByRole('button', { name: /refreshing/i })).toBeDisabled()

    overviewGate.release?.()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /^refresh$/i })).toBeEnabled()
    })

    expect(screen.getByLabelText(/top \/ category limit/i)).toHaveValue('5')
    expect(screen.getByLabelText(/from \(utc day\)/i)).toHaveValue('2026-05-01')
    await waitFor(() => {
      expect(requested.filter((url) => url.includes('/admin/analytics/overview')).length).toBeGreaterThan(0)
      expect(requested.filter((url) => url.includes('/admin/analytics/orders/status')).length).toBeGreaterThan(0)
      expect(requested.filter((url) => url.includes('/admin/analytics/orders/timeseries')).length).toBeGreaterThan(0)
      expect(requested.filter((url) => url.includes('/admin/analytics/products/top')).length).toBeGreaterThan(0)
      expect(requested.filter((url) => url.includes('/admin/analytics/categories/sales')).length).toBeGreaterThan(0)
      expect(requested.filter((url) => url.includes('/admin/analytics/promotions/summary')).length).toBeGreaterThan(0)
    })
    expect(requested.every((url) => url.includes('from=2026-05-01'))).toBe(true)
    expect(requested.every((url) => url.includes('to=2026-05-31'))).toBe(true)
    expect(requested.some((url) => url.includes('limit=5'))).toBe(true)
  })

  it('surfaces refresh failure without clearing KPIs', async () => {
    const user = userEvent.setup()
    let failRefresh = false
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoAdminSession)
      }
      if (failRefresh && url.includes('/admin/analytics/overview')) {
        return jsonResponse(500, { status: 500, error: 'Error', message: 'overview refresh failed' })
      }
      if (url.includes('/admin/analytics/overview')) {
        return jsonResponse(200, overviewBody)
      }
      if (url.includes('/admin/analytics/orders/status')) {
        return jsonResponse(200, statusBody)
      }
      if (url.includes('/admin/analytics/orders/timeseries')) {
        return jsonResponse(200, timeseriesBody)
      }
      if (url.includes('/admin/analytics/products/top')) {
        return jsonResponse(200, topBody)
      }
      if (url.includes('/admin/analytics/categories/sales')) {
        return jsonResponse(200, categoryBody)
      }
      if (url.includes('/admin/analytics/promotions/summary')) {
        return jsonResponse(200, promotionsBody)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/analytics?from=2026-05-01T00:00:00Z&to=2026-05-31T00:00:00Z',
      session: demoAdminSession,
    })

    await waitFor(() => {
      expect(screen.getByText(/270/)).toBeInTheDocument()
    })

    failRefresh = true
    await user.click(screen.getByRole('button', { name: /^refresh$/i }))
    await waitFor(() => {
      expect(screen.getAllByText(/overview refresh failed/i).length).toBeGreaterThan(0)
    })
    expect(screen.getByText(/270/)).toBeInTheDocument()
  })
})
