import { describe, expect, it } from 'vitest'

import {
  FOODMART_HOMEPAGE_FALLBACK,
  isEmptyHomepagePromotions,
  withFoodmartHomepageFallback,
} from './foodmart-fallback'
import type { HomepagePromotions, PublicPlacement } from '../types'

const liveHero: PublicPlacement = {
  id: 'h1',
  campaignId: 'c1',
  placementKey: 'HOME_HERO',
  title: 'Live Hero',
  subtitle: 'From API',
  ctaLabel: 'Shop',
  cta: {
    type: 'NONE',
    productId: null,
    categoryId: null,
    collectionKey: null,
    url: null,
    href: '/products',
  },
  desktopImageUrl: '/uploads/promotions/9d1f7e4b-1111-4111-8111-111111111111.jpg',
  mobileImageUrl: null,
  displayOrder: 0,
}

const liveTop: PublicPlacement = {
  ...liveHero,
  id: 't1',
  placementKey: 'HOME_PROMO_TOP',
  title: 'Live Top',
}

describe('withFoodmartHomepageFallback', () => {
  it('returns full FoodMart fallback for null/empty payloads', () => {
    expect(withFoodmartHomepageFallback(null)).toEqual(FOODMART_HOMEPAGE_FALLBACK)
    expect(
      withFoodmartHomepageFallback({ hero: [], promoTop: null, promoBottom: null }),
    ).toEqual(FOODMART_HOMEPAGE_FALLBACK)
    expect(isEmptyHomepagePromotions({ hero: [], promoTop: null, promoBottom: null })).toBe(true)
  })

  it('keeps live hero and fills null promo slots from FoodMart', () => {
    const merged = withFoodmartHomepageFallback({
      hero: [liveHero],
      promoTop: null,
      promoBottom: null,
    })
    expect(merged.hero).toEqual([liveHero])
    expect(merged.promoTop?.title).toBe(FOODMART_HOMEPAGE_FALLBACK.promoTop?.title)
    expect(merged.promoBottom?.title).toBe(FOODMART_HOMEPAGE_FALLBACK.promoBottom?.title)
  })

  it('preserves live promoTop/promoBottom when present', () => {
    const input: HomepagePromotions = {
      hero: [liveHero],
      promoTop: liveTop,
      promoBottom: { ...liveTop, id: 'b1', placementKey: 'HOME_PROMO_BOTTOM', title: 'Live Bottom' },
    }
    expect(withFoodmartHomepageFallback(input)).toEqual(input)
  })
})
