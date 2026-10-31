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

const strategy = {
  id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  name: 'Banner Strat',
  code: 'BANNER',
  type: 'BANNER_ONLY',
  discountPercent: null,
  discountAmount: null,
  maxDiscountAmount: null,
  minOrderAmount: null,
  minQuantity: null,
  stackable: false,
  priority: 10,
  active: true,
  createdAt: '2026-10-31T00:00:00Z',
  updatedAt: '2026-10-31T00:00:00Z',
}

const campaign = {
  id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  name: 'Weekend Banner',
  slug: 'weekend-banner',
  status: 'DRAFT',
  effectiveStatus: 'DRAFT',
  pricingEffective: false,
  placementEffective: false,
  startsAt: '2026-11-01T00:00:00Z',
  endsAt: '2026-11-07T23:59:59Z',
  priority: 10,
  stackable: false,
  notes: null,
  strategy: {
    id: strategy.id,
    name: strategy.name,
    code: strategy.code,
    type: strategy.type,
    active: true,
  },
  productIds: [],
  categoryIds: [],
  placementCount: 0,
  createdAt: '2026-10-31T00:00:00Z',
  updatedAt: '2026-10-31T00:00:00Z',
}

const category = {
  id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
  name: 'Produce',
  slug: 'produce',
  description: null,
  parentId: null,
  active: true,
}

const product = {
  id: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd',
  sku: 'APPLE-1',
  name: 'Apples',
  description: null,
  unitPrice: 10000,
  currency: 'VND',
  categoryId: category.id,
  categoryName: category.name,
  active: true,
  imageUrl: null,
  inStock: true,
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

describe('Admin promotion pages', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('blocks CUSTOMER from promotion admin routes', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      return jsonResponse(403, { status: 403, error: 'Forbidden', message: 'Forbidden' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/promotions',
      session: demoCustomerSession,
    })

    expect(await screen.findByRole('heading', { name: /access denied/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /^promotions$/i })).not.toBeInTheDocument()
  })

  it('renders promotions hub and strategy list for ADMIN', async () => {
    mockAdminFetch((url) => {
      if (url.includes('/admin/promotions/strategies')) {
        return jsonResponse(200, [strategy])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/promotions',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /^promotions$/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /open strategies/i })).toHaveAttribute(
      'href',
      '/admin/promotions/strategies',
    )

    renderWithProviders(<AppRouter />, {
      route: '/admin/promotions/strategies',
      session: demoAdminSession,
    })
    expect(await screen.findByRole('heading', { name: /promotion strategies/i })).toBeInTheDocument()
    expect(await screen.findByText('Banner Strat')).toBeInTheDocument()
    expect(screen.getByText('BANNER_ONLY')).toBeInTheDocument()
    expect(screen.getByText(/showing page 1 of 1 \(1 total\)/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^previous$/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /^next$/i })).toBeDisabled()
  })

  it('paginates strategy list client-side and clamps invalid page', async () => {
    const user = userEvent.setup()
    const many = Array.from({ length: 12 }, (_, i) => ({
      ...strategy,
      id: `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa${i.toString(16).padStart(2, '0')}`,
      name: `Strategy ${i + 1}`,
      code: `STRAT-${i + 1}`,
      priority: 100 - i,
    }))

    mockAdminFetch((url) => {
      if (url.includes('/admin/promotions/strategies')) {
        return jsonResponse(200, many)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/promotions/strategies',
      session: demoAdminSession,
    })

    expect(await screen.findByText('Strategy 1')).toBeInTheDocument()
    expect(screen.getByText('Strategy 10')).toBeInTheDocument()
    expect(screen.queryByText('Strategy 11')).not.toBeInTheDocument()
    expect(screen.getByText(/showing page 1 of 2 \(12 total\)/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^previous$/i })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: /^next$/i }))
    expect(await screen.findByText('Strategy 11')).toBeInTheDocument()
    expect(screen.getByText('Strategy 12')).toBeInTheDocument()
    expect(screen.queryByText('Strategy 1')).not.toBeInTheDocument()
    expect(screen.getByText(/showing page 2 of 2 \(12 total\)/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^next$/i })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: /^previous$/i }))
    expect(await screen.findByText('Strategy 1')).toBeInTheDocument()

    renderWithProviders(<AppRouter />, {
      route: '/admin/promotions/strategies?page=99',
      session: demoAdminSession,
    })
    expect(await screen.findByText(/showing page 2 of 2 \(12 total\)/i)).toBeInTheDocument()
    expect(screen.getByText('Strategy 11')).toBeInTheDocument()
  })

  it('shows empty strategy list without pagination controls', async () => {
    mockAdminFetch((url) => {
      if (url.includes('/admin/promotions/strategies')) {
        return jsonResponse(200, [])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/promotions/strategies',
      session: demoAdminSession,
    })

    expect(await screen.findByText(/no strategies yet/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^previous$/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^next$/i })).not.toBeInTheDocument()
  })

  it('creates a BANNER_ONLY strategy successfully', async () => {
    const user = userEvent.setup()
    mockAdminFetch((url, init) => {
      if (url.includes('/admin/promotions/strategies') && init?.method === 'POST') {
        return jsonResponse(201, { ...strategy, name: 'New Banner' })
      }
      if (url.endsWith('/admin/promotions/strategies') || url.includes('/strategies?')) {
        return jsonResponse(200, [])
      }
      if (url.includes(`/admin/promotions/strategies/${strategy.id}`)) {
        return jsonResponse(200, { ...strategy, name: 'New Banner' })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/promotions/strategies/new',
      session: demoAdminSession,
    })

    await user.type(await screen.findByLabelText(/^name$/i), 'New Banner')
    await user.click(screen.getByRole('button', { name: /save strategy/i }))

    expect(await screen.findByRole('heading', { name: /edit strategy/i })).toBeInTheDocument()
  })

  it('lists campaigns with UTC windows and manages activate/pause/preview', async () => {
    const user = userEvent.setup()
    let current = { ...campaign }

    mockAdminFetch((url, init) => {
      if (url.includes('/admin/promotions/campaigns') && !url.includes(campaign.id) && (!init?.method || init.method === 'GET')) {
        return jsonResponse(200, [current])
      }
      if (url.includes(`/admin/promotions/campaigns/${campaign.id}/activate`) && init?.method === 'POST') {
        current = {
          ...current,
          status: 'ACTIVE',
          effectiveStatus: 'ACTIVE',
          placementEffective: true,
        }
        return jsonResponse(200, current)
      }
      if (url.includes(`/admin/promotions/campaigns/${campaign.id}/pause`) && init?.method === 'POST') {
        current = {
          ...current,
          status: 'PAUSED',
          effectiveStatus: 'PAUSED',
          placementEffective: false,
        }
        return jsonResponse(200, current)
      }
      if (url.includes(`/admin/promotions/campaigns/${campaign.id}/preview`) && init?.method === 'POST') {
        return jsonResponse(200, {
          evaluatedAt: '2026-11-02T12:00:00Z',
          status: 'ACTIVE',
          effectiveStatus: 'ACTIVE',
          pricingEffective: false,
          placementEffective: true,
          note: 'Discount amount calculation is deferred to checkout (P22)',
        })
      }
      if (url.includes(`/admin/promotions/campaigns/${campaign.id}`) && (!init?.method || init.method === 'GET')) {
        return jsonResponse(200, current)
      }
      if (url.includes('/admin/promotions/strategies')) {
        return jsonResponse(200, [strategy])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/promotions/campaigns',
      session: demoAdminSession,
    })

    expect(await screen.findByText('Weekend Banner')).toBeInTheDocument()
    expect(screen.getByText('2026-11-01T00:00:00.000Z')).toBeInTheDocument()

    renderWithProviders(<AppRouter />, {
      route: `/admin/promotions/campaigns/${campaign.id}?tab=details`,
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /weekend banner/i })).toBeInTheDocument()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await user.click(screen.getByRole('button', { name: /^activate$/i }))
    await waitFor(() => {
      expect(screen.getByText(/campaign lifecycle updated/i)).toBeInTheDocument()
    })

    await user.click(screen.getByRole('tab', { name: /preview/i }))
    await user.click(screen.getByRole('button', { name: /run preview/i }))
    expect(await screen.findByText('pricingEffective')).toBeInTheDocument()
    expect(screen.getByText('false')).toBeInTheDocument()
    expect(screen.getByText('placementEffective')).toBeInTheDocument()
  })

  it('updates eligibility and creates a placement with promotion upload', async () => {
    const user = userEvent.setup()
    const uploaded = '/uploads/promotions/9d1f7e4b-1111-4111-8111-111111111111.jpg'
    let current = { ...campaign, productIds: [] as string[], categoryIds: [] as string[] }
    let placements: unknown[] = []

    mockAdminFetch((url, init) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      if (url.includes('/admin/products')) {
        return jsonResponse(200, {
          items: [product],
          page: 0,
          size: 100,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        })
      }
      if (url.includes('/admin/promotions/strategies')) {
        return jsonResponse(200, [strategy])
      }
      if (url.includes('/admin/uploads/images') && url.includes('purpose=PROMOTION') && init?.method === 'POST') {
        return jsonResponse(200, {
          imageUrl: uploaded,
          contentType: 'image/jpeg',
          sizeBytes: 12,
        })
      }
      if (url.includes(`/admin/promotions/campaigns/${campaign.id}/eligibility`) && init?.method === 'PUT') {
        const body = JSON.parse(String(init.body)) as { productIds: string[]; categoryIds: string[] }
        current = { ...current, productIds: body.productIds, categoryIds: body.categoryIds }
        return jsonResponse(200, current)
      }
      if (url.includes(`/admin/promotions/campaigns/${campaign.id}/placements`) && init?.method === 'POST') {
        const body = JSON.parse(String(init.body)) as Record<string, unknown>
        const created = {
          id: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
          campaignId: campaign.id,
          placementKey: body.placementKey,
          title: body.title,
          subtitle: body.subtitle ?? null,
          ctaLabel: body.ctaLabel ?? null,
          ctaType: body.ctaType ?? 'NONE',
          ctaProductId: body.ctaProductId ?? null,
          ctaCategoryId: body.ctaCategoryId ?? null,
          ctaCollectionKey: body.ctaCollectionKey ?? null,
          ctaUrl: body.ctaUrl ?? null,
          desktopImageUrl: body.desktopImageUrl,
          mobileImageUrl: body.mobileImageUrl ?? null,
          displayOrder: body.displayOrder ?? 0,
          startsAt: null,
          endsAt: null,
          enabled: true,
          createdAt: '2026-10-31T00:00:00Z',
          updatedAt: '2026-10-31T00:00:00Z',
        }
        placements = [created]
        return jsonResponse(201, created)
      }
      if (url.includes(`/admin/promotions/campaigns/${campaign.id}/placements`)) {
        return jsonResponse(200, placements)
      }
      if (url.includes(`/admin/promotions/campaigns/${campaign.id}`)) {
        return jsonResponse(200, current)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: `/admin/promotions/campaigns/${campaign.id}?tab=eligibility`,
      session: demoAdminSession,
    })

    expect(await screen.findByRole('tab', { name: /eligibility/i })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await user.click(await screen.findByLabelText(/apples \(apple-1\)/i))
    await user.click(screen.getByRole('button', { name: /save eligibility/i }))
    await waitFor(() => {
      expect(screen.getByText(/eligibility updated/i)).toBeInTheDocument()
    })

    await user.click(screen.getByRole('tab', { name: /placements/i }))
    await user.click(await screen.findByRole('button', { name: /add placement/i }))
    await user.type(screen.getByLabelText(/^title$/i), 'Hero Promo')

    const file = new File([new Uint8Array([1, 2, 3, 4])], 'hero.jpg', { type: 'image/jpeg' })
    const fileInputs = document.querySelectorAll('input[type="file"]')
    expect(fileInputs.length).toBeGreaterThan(0)
    await user.upload(fileInputs[0] as HTMLInputElement, file)

    await waitFor(() => {
      expect(screen.getByText(uploaded)).toBeInTheDocument()
    })

    await user.click(screen.getByRole('button', { name: /save placement/i }))
    await waitFor(() => {
      expect(screen.getByText(/placement created/i)).toBeInTheDocument()
    })
    expect(screen.getByText('Hero Promo')).toBeInTheDocument()
  })

  it('shows API error message when activate is rejected', async () => {
    const user = userEvent.setup()
    mockAdminFetch((url, init) => {
      if (url.includes(`/admin/promotions/campaigns/${campaign.id}/activate`) && init?.method === 'POST') {
        return jsonResponse(400, {
          status: 400,
          error: 'Bad Request',
          message: 'Cannot activate a campaign after its endsAt',
        })
      }
      if (url.includes(`/admin/promotions/campaigns/${campaign.id}`)) {
        return jsonResponse(200, campaign)
      }
      if (url.includes('/admin/promotions/strategies')) {
        return jsonResponse(200, [strategy])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: `/admin/promotions/campaigns/${campaign.id}`,
      session: demoAdminSession,
    })

    expect(await screen.findByRole('button', { name: /^activate$/i })).toBeInTheDocument()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    await user.click(screen.getByRole('button', { name: /^activate$/i }))
    expect(await screen.findByText(/cannot activate a campaign after its endsat/i)).toBeInTheDocument()
  })
})
