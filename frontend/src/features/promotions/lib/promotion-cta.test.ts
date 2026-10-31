import { describe, expect, it } from 'vitest'

import { promotionCtaLabel, resolvePromotionCta } from './promotion-cta'
import type { PublicPlacement } from '../types'

function placement(cta: PublicPlacement['cta'], ctaLabel: string | null = 'Shop'): PublicPlacement {
  return {
    id: '1',
    campaignId: '2',
    placementKey: 'HOME_HERO',
    title: 'T',
    subtitle: null,
    ctaLabel,
    cta,
    desktopImageUrl: '/uploads/promotions/9d1f7e4b-1111-4111-8111-111111111111.jpg',
    mobileImageUrl: null,
    displayOrder: 0,
  }
}

describe('resolvePromotionCta', () => {
  it('maps internal href paths from the API', () => {
    expect(
      resolvePromotionCta(
        placement({
          type: 'PRODUCT',
          productId: 'p1',
          categoryId: null,
          collectionKey: null,
          url: null,
          href: '/products/p1',
        }),
      ),
    ).toEqual({ kind: 'internal', to: '/products/p1' })
  })

  it('maps absolute http(s) URL CTAs as external', () => {
    expect(
      resolvePromotionCta(
        placement({
          type: 'URL',
          productId: null,
          categoryId: null,
          collectionKey: null,
          url: 'https://example.com/deal',
          href: 'https://example.com/deal',
        }),
      ),
    ).toEqual({ kind: 'external', href: 'https://example.com/deal' })
  })

  it('returns none when CTA is missing or unsafe', () => {
    expect(resolvePromotionCta(placement(null))).toEqual({ kind: 'none' })
    expect(
      resolvePromotionCta(
        placement({
          type: 'NONE',
          productId: null,
          categoryId: null,
          collectionKey: null,
          url: null,
          href: null,
        }),
      ),
    ).toEqual({ kind: 'none' })
    expect(
      resolvePromotionCta(
        placement({
          type: 'URL',
          productId: null,
          categoryId: null,
          collectionKey: null,
          url: 'javascript:evil',
          href: 'javascript:evil',
        }),
      ),
    ).toEqual({ kind: 'none' })
  })

  it('uses ctaLabel with a sensible default', () => {
    expect(promotionCtaLabel(placement(null, 'Buy now'))).toBe('Buy now')
    expect(promotionCtaLabel(placement(null, '  '))).toBe('Shop now')
  })
})
