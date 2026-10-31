import type { HomepagePromotions, PublicPlacement } from '../types'

/** Existing FoodMart banner assets — used only when API placements are empty/missing/failed (ADR-020 R3). */
const HERO_1 = '/assets/foodmart/images/banners/banner-image-1.jpg'
const HERO_2 = '/assets/foodmart/images/banners/slide-1.jpg'
const PROMO_TOP = '/assets/foodmart/images/banners/ad-image-1.png'
const PROMO_BOTTOM = '/assets/foodmart/images/banners/ad-image-2.png'

function fallbackPlacement(
  id: string,
  key: PublicPlacement['placementKey'],
  title: string,
  subtitle: string,
  image: string,
  order: number,
): PublicPlacement {
  return {
    id,
    campaignId: 'foodmart-fallback',
    placementKey: key,
    title,
    subtitle,
    ctaLabel: 'Browse catalog',
    cta: {
      type: 'NONE',
      productId: null,
      categoryId: null,
      collectionKey: null,
      url: null,
      href: '/products',
    },
    desktopImageUrl: image,
    mobileImageUrl: null,
    displayOrder: order,
  }
}

export const FOODMART_HOMEPAGE_FALLBACK: HomepagePromotions = {
  hero: [
    fallbackPlacement(
      'foodmart-hero-1',
      'HOME_HERO',
      'Fresh groceries for your kitchen',
      'Browse the live Aptech-4 catalog — prices and stock from the API.',
      HERO_1,
      0,
    ),
    fallbackPlacement(
      'foodmart-hero-2',
      'HOME_HERO',
      'Stock up for the week',
      'Decorative FoodMart artwork used when no Admin placements are live.',
      HERO_2,
      1,
    ),
  ],
  promoTop: fallbackPlacement(
    'foodmart-promo-top',
    'HOME_PROMO_TOP',
    'Fresh from the produce aisle',
    'Explore fruits, vegetables, and pantry staples.',
    PROMO_TOP,
    0,
  ),
  promoBottom: fallbackPlacement(
    'foodmart-promo-bottom',
    'HOME_PROMO_BOTTOM',
    'Weekly grocery essentials',
    'Sign in to cart and try the demo checkout.',
    PROMO_BOTTOM,
    0,
  ),
}

/**
 * Merge API homepage payload with per-slot FoodMart fallback (R3).
 * Empty hero list → fallback heroes; null promo slots → matching fallback banners.
 */
export function withFoodmartHomepageFallback(
  data: HomepagePromotions | null | undefined,
): HomepagePromotions {
  if (!data) {
    return FOODMART_HOMEPAGE_FALLBACK
  }
  const hero = Array.isArray(data.hero) && data.hero.length > 0 ? data.hero : FOODMART_HOMEPAGE_FALLBACK.hero
  return {
    hero,
    promoTop: data.promoTop ?? FOODMART_HOMEPAGE_FALLBACK.promoTop,
    promoBottom: data.promoBottom ?? FOODMART_HOMEPAGE_FALLBACK.promoBottom,
  }
}

export function isEmptyHomepagePromotions(data: HomepagePromotions | null | undefined): boolean {
  if (!data) {
    return true
  }
  const heroEmpty = !Array.isArray(data.hero) || data.hero.length === 0
  return heroEmpty && data.promoTop == null && data.promoBottom == null
}
