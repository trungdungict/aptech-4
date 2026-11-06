import { describe, expect, it } from 'vitest'

import {
  foodmartHomepageFallbackFor,
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
  it('builds VI FoodMart fallback from promotions:fallback.* for null/empty payloads', () => {
    expect(foodmartHomepageFallbackFor('vi').hero[0]?.title).toBe('Thực phẩm tươi cho gian bếp')
    expect(withFoodmartHomepageFallback(null, 'vi')).toEqual(foodmartHomepageFallbackFor('vi'))
    expect(
      withFoodmartHomepageFallback({ hero: [], promoTop: null, promoBottom: null }, 'vi'),
    ).toEqual(foodmartHomepageFallbackFor('vi'))
    expect(isEmptyHomepagePromotions({ hero: [], promoTop: null, promoBottom: null })).toBe(true)
  })

  it('selects EN copy when locale is en', () => {
    const en = foodmartHomepageFallbackFor('en')
    expect(en.hero[0]?.title).toBe('Fresh groceries for your kitchen')
    expect(withFoodmartHomepageFallback(null, 'en')).toEqual(en)
  })

  it('defaults to the active app locale (tests run in EN)', () => {
    expect(withFoodmartHomepageFallback(null).hero[0]?.title).toBe('Fresh groceries for your kitchen')
  })

  it('keeps live hero and fills null promo slots from FoodMart', () => {
    const merged = withFoodmartHomepageFallback(
      {
        hero: [liveHero],
        promoTop: null,
        promoBottom: null,
      },
      'vi',
    )
    expect(merged.hero).toEqual([liveHero])
    expect(merged.promoTop?.title).toBe(foodmartHomepageFallbackFor('vi').promoTop?.title)
    expect(merged.promoBottom?.title).toBe(foodmartHomepageFallbackFor('vi').promoBottom?.title)
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
