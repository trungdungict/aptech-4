import { describe, expect, it } from 'vitest'

import { promotionQueryKeys } from '@/features/promotions/hooks/promotion-query-keys'

import { catalogQueryKeys } from './catalog-query-keys'

describe('locale-aware query keys', () => {
  it('separates catalog keys by locale', () => {
    expect(catalogQueryKeys.categories('vi')).not.toEqual(catalogQueryKeys.categories('en'))
    expect(catalogQueryKeys.categories('vi')).toEqual(['catalog', 'categories', 'vi'])
    expect(catalogQueryKeys.productDetail('en', 'abc')).toEqual([
      'catalog',
      'products',
      'en',
      'detail',
      'abc',
    ])
  })

  it('separates promotion homepage keys by locale', () => {
    expect(promotionQueryKeys.homepage('vi')).toEqual(['promotions', 'homepage', 'vi'])
    expect(promotionQueryKeys.homepage('en')).toEqual(['promotions', 'homepage', 'en'])
  })
})
