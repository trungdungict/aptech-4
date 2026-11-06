import { getActiveLocale, i18n, type AppLocale } from '@/lib/i18n'

import type { HomepagePromotions, PublicPlacement } from '../types'

/** Existing FoodMart banner assets — used only when API placements are empty/missing/failed (ADR-020 R3). */
const HERO_1 = '/assets/foodmart/images/banners/banner-image-1.jpg'
const HERO_2 = '/assets/foodmart/images/banners/slide-1.jpg'
const PROMO_TOP = '/assets/foodmart/images/banners/ad-image-1.png'
const PROMO_BOTTOM = '/assets/foodmart/images/banners/ad-image-2.png'

type FallbackCopy = {
  title: string
  subtitle: string
  ctaLabel: string
}

type FallbackSlot = 'hero1' | 'hero2' | 'promoTop' | 'promoBottom'

/** Copy comes from `promotions:fallback.*` for the requested locale (VI default, EN available). */
function fallbackCopy(slot: FallbackSlot, locale: AppLocale): FallbackCopy {
  const t = (key: `fallback.${string}`) =>
    i18n.t(`promotions:${key}` as 'promotions:fallback.cta', { lng: locale })
  return {
    title: t(`fallback.${slot}Title`),
    subtitle: t(`fallback.${slot}Subtitle`),
    ctaLabel: t('fallback.cta'),
  }
}

function fallbackPlacement(
  id: string,
  key: PublicPlacement['placementKey'],
  copy: FallbackCopy,
  image: string,
  order: number,
): PublicPlacement {
  return {
    id,
    campaignId: 'foodmart-fallback',
    placementKey: key,
    title: copy.title,
    subtitle: copy.subtitle,
    ctaLabel: copy.ctaLabel,
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

/** FoodMart artwork + copy for the given locale. Call after `initI18n()` (copy is resolved at call time). */
export function foodmartHomepageFallbackFor(locale: AppLocale): HomepagePromotions {
  return {
    hero: [
      fallbackPlacement('foodmart-hero-1', 'HOME_HERO', fallbackCopy('hero1', locale), HERO_1, 0),
      fallbackPlacement('foodmart-hero-2', 'HOME_HERO', fallbackCopy('hero2', locale), HERO_2, 1),
    ],
    promoTop: fallbackPlacement(
      'foodmart-promo-top',
      'HOME_PROMO_TOP',
      fallbackCopy('promoTop', locale),
      PROMO_TOP,
      0,
    ),
    promoBottom: fallbackPlacement(
      'foodmart-promo-bottom',
      'HOME_PROMO_BOTTOM',
      fallbackCopy('promoBottom', locale),
      PROMO_BOTTOM,
      0,
    ),
  }
}

/**
 * Merge API homepage payload with per-slot FoodMart fallback (R3).
 * Empty hero list → fallback heroes; null promo slots → matching fallback banners.
 */
export function withFoodmartHomepageFallback(
  data: HomepagePromotions | null | undefined,
  locale: AppLocale = getActiveLocale(),
): HomepagePromotions {
  const fallback = foodmartHomepageFallbackFor(locale)
  if (!data) {
    return fallback
  }
  const hero = Array.isArray(data.hero) && data.hero.length > 0 ? data.hero : fallback.hero
  return {
    hero,
    promoTop: data.promoTop ?? fallback.promoTop,
    promoBottom: data.promoBottom ?? fallback.promoBottom,
  }
}

export function isEmptyHomepagePromotions(data: HomepagePromotions | null | undefined): boolean {
  if (!data) {
    return true
  }
  const heroEmpty = !Array.isArray(data.hero) || data.hero.length === 0
  return heroEmpty && data.promoTop == null && data.promoBottom == null
}
