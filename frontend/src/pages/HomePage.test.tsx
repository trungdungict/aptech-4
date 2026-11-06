import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { foodmartHomepageFallbackFor } from '@/features/promotions/lib/foodmart-fallback'
import { changeAppLocale, i18n } from '@/lib/i18n'
import { renderWithProviders } from '@/test/render-app'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const livePlacement = {
  id: '11111111-1111-4111-8111-111111111111',
  campaignId: '22222222-2222-4222-8222-222222222222',
  placementKey: 'HOME_HERO',
  title: 'API Weekend Produce',
  subtitle: '20% off leafy greens',
  ctaLabel: 'Shop produce',
  cta: {
    type: 'CATEGORY',
    productId: null,
    categoryId: '33333333-3333-4333-8333-333333333333',
    collectionKey: null,
    url: null,
    href: '/products?categoryId=33333333-3333-4333-8333-333333333333',
  },
  desktopImageUrl: '/uploads/promotions/9d1f7e4b-1111-4111-8111-111111111111.jpg',
  mobileImageUrl: null,
  displayOrder: 0,
}

const liveTop = {
  ...livePlacement,
  id: '44444444-4444-4444-8444-444444444444',
  placementKey: 'HOME_PROMO_TOP',
  title: 'API Promo Top',
  subtitle: 'Top banner from Admin',
  ctaLabel: 'View deals',
  desktopImageUrl: '/uploads/promotions/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png',
}

const liveBottom = {
  ...livePlacement,
  id: '55555555-5555-4555-8555-555555555555',
  placementKey: 'HOME_PROMO_BOTTOM',
  title: 'API Promo Bottom',
  subtitle: 'Bottom banner from Admin',
  ctaLabel: 'Browse all',
  cta: {
    type: 'NONE',
    productId: null,
    categoryId: null,
    collectionKey: null,
    url: null,
    href: '/products',
  },
  desktopImageUrl: '/uploads/promotions/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.webp',
}

function stubCatalogAndPromotions(
  homepage:
    | { hero: unknown[]; promoTop: unknown; promoBottom: unknown }
    | 'error'
    | 'empty' = 'empty',
) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input) => {
      const url = String(input)
      if (url.includes('/promotions/homepage')) {
        if (homepage === 'error') {
          return jsonResponse(500, { status: 500, error: 'Error', message: 'boom' })
        }
        if (homepage === 'empty') {
          return jsonResponse(200, { hero: [], promoTop: null, promoBottom: null })
        }
        return jsonResponse(200, homepage)
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
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    }),
  )
}

describe('storefront home', () => {
  beforeEach(() => {
    clearSession()
    stubCatalogAndPromotions('empty')
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

    expect(await screen.findByRole('heading', { name: /featured offers/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /grocery categories/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /trending products/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /best-selling products/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /just arrived/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /why shop here/i })).toBeInTheDocument()
  })

  it('renders live homepage promotions from the public API', async () => {
    stubCatalogAndPromotions({
      hero: [livePlacement],
      promoTop: liveTop,
      promoBottom: liveBottom,
    })
    renderWithProviders(<AppRouter />, { route: '/' })

    expect(await screen.findByText('API Weekend Produce')).toBeInTheDocument()
    expect(screen.getByText('API Promo Top')).toBeInTheDocument()
    expect(screen.getByText('API Promo Bottom')).toBeInTheDocument()

    const heroLink = screen.getByRole('link', { name: /api weekend produce/i })
    expect(heroLink).toHaveAttribute(
      'href',
      '/products?categoryId=33333333-3333-4333-8333-333333333333',
    )
    expect(heroLink.querySelector('img')).toHaveAttribute(
      'src',
      '/uploads/promotions/9d1f7e4b-1111-4111-8111-111111111111.jpg',
    )

    const topLink = screen.getByRole('link', { name: /api promo top/i })
    expect(topLink.querySelector('img')).toHaveAttribute(
      'src',
      '/uploads/promotions/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png',
    )
  })

  it('falls back to FoodMart artwork when the homepage API returns empty slots', async () => {
    stubCatalogAndPromotions('empty')
    renderWithProviders(<AppRouter />, { route: '/' })

    expect(
      await screen.findByText(foodmartHomepageFallbackFor('en').hero[0]!.title),
    ).toBeInTheDocument()
    expect(screen.getByText(foodmartHomepageFallbackFor('en').promoTop!.title)).toBeInTheDocument()
    expect(screen.getByText(foodmartHomepageFallbackFor('en').promoBottom!.title)).toBeInTheDocument()
    expect(screen.queryByText('API Weekend Produce')).not.toBeInTheDocument()
  })

  it('falls back to FoodMart artwork when the homepage API fails', async () => {
    stubCatalogAndPromotions('error')
    renderWithProviders(<AppRouter />, { route: '/' })

    expect(
      await screen.findByText(/showing foodmart artwork while promotions are unavailable/i),
    ).toBeInTheDocument()
    expect(screen.getByText(foodmartHomepageFallbackFor('en').hero[0]!.title)).toBeInTheDocument()
  })

  it('fills null promoTop/promoBottom with FoodMart while keeping live hero', async () => {
    stubCatalogAndPromotions({
      hero: [livePlacement],
      promoTop: null,
      promoBottom: null,
    })
    renderWithProviders(<AppRouter />, { route: '/' })

    expect(await screen.findByText('API Weekend Produce')).toBeInTheDocument()
    expect(screen.getByText(foodmartHomepageFallbackFor('en').promoTop!.title)).toBeInTheDocument()
    expect(screen.getByText(foodmartHomepageFallbackFor('en').promoBottom!.title)).toBeInTheDocument()
  })

  it('advances hero carousel controls when multiple slides are present', async () => {
    const user = userEvent.setup()
    stubCatalogAndPromotions({
      hero: [
        livePlacement,
        {
          ...livePlacement,
          id: '66666666-6666-4666-8666-666666666666',
          title: 'Second API Hero',
          desktopImageUrl: '/uploads/promotions/cccccccc-cccc-4ccc-8ccc-cccccccccccc.jpg',
        },
      ],
      promoTop: liveTop,
      promoBottom: liveBottom,
    })
    renderWithProviders(<AppRouter />, { route: '/' })

    expect(await screen.findByText('API Weekend Produce')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /next slide/i }))
    await waitFor(() => {
      expect(screen.getByText('Second API Hero')).toBeInTheDocument()
    })
  })

  it('renders Vietnamese hero and FoodMart fallback copy after switching locale', async () => {
    await changeAppLocale('vi')
    renderWithProviders(<AppRouter />, { route: '/' })

    expect(
      await screen.findByRole('heading', { name: i18n.t('home:hero.title') }),
    ).toBeInTheDocument()
    expect(await screen.findByText(i18n.t('promotions:fallback.hero1Title'))).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: i18n.t('common:nav.signIn') }).length).toBeGreaterThan(
      0,
    )
  })
})
