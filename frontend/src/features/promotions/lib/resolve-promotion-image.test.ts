import { describe, expect, it } from 'vitest'

import { resolvePromotionImageUrl } from './resolve-promotion-image'

describe('resolvePromotionImageUrl', () => {
  it('prefers desktop path-absolute upload URLs by default', () => {
    expect(
      resolvePromotionImageUrl(
        '/uploads/promotions/9d1f7e4b-1111-4111-8111-111111111111.jpg',
        '/uploads/promotions/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.webp',
        false,
      ),
    ).toBe('/uploads/promotions/9d1f7e4b-1111-4111-8111-111111111111.jpg')
  })

  it('prefers mobile when requested and present', () => {
    expect(
      resolvePromotionImageUrl(
        '/uploads/promotions/9d1f7e4b-1111-4111-8111-111111111111.jpg',
        '/uploads/promotions/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.webp',
        true,
      ),
    ).toBe('/uploads/promotions/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.webp')
  })

  it('falls back to desktop when mobile is blank', () => {
    expect(
      resolvePromotionImageUrl('/uploads/promotions/9d1f7e4b-1111-4111-8111-111111111111.jpg', '  ', true),
    ).toBe('/uploads/promotions/9d1f7e4b-1111-4111-8111-111111111111.jpg')
  })

  it('accepts absolute http(s) URLs and rejects empty or unsafe values', () => {
    expect(resolvePromotionImageUrl('https://cdn.example/promo.jpg')).toBe(
      'https://cdn.example/promo.jpg',
    )
    expect(resolvePromotionImageUrl(null, null)).toBeNull()
    expect(resolvePromotionImageUrl('javascript:alert(1)')).toBeNull()
    expect(resolvePromotionImageUrl('/uploads/../etc/passwd')).toBeNull()
  })
})
