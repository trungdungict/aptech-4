import { describe, expect, it } from 'vitest'

import { foodmartSkuImageMap, resolveCategoryIcon, resolveProductImage } from './resolve-product-image'

describe('resolveProductImage', () => {
  it('prefers a non-empty API imageUrl', () => {
    expect(
      resolveProductImage({
        sku: 'FOOD-FRUIT-BANANA-001',
        imageUrl: 'https://cdn.example/x.png',
      }),
    ).toBe('https://cdn.example/x.png')
  })

  it('resolves known FoodMart SKUs from the local map', () => {
    expect(resolveProductImage({ sku: 'FOOD-FRUIT-BANANA-001' })).toBe(
      '/assets/foodmart/images/products/thumb-bananas.png',
    )
    expect(foodmartSkuImageMap['FOOD-DAIRY-MILK-001']).toMatch(/thumb-milk\.png$/)
  })

  it('falls back to name keywords when SKU is unmapped', () => {
    expect(resolveProductImage({ sku: 'X-1', name: 'Fresh Avocado Pack' })).toBe(
      '/assets/foodmart/images/products/thumb-avocado.png',
    )
  })

  it('returns null when nothing matches (neutral placeholder)', () => {
    expect(resolveProductImage({ sku: 'UNKNOWN-SKU', name: 'Mystery Widget' })).toBeNull()
  })
})

describe('resolveCategoryIcon', () => {
  it('maps grocery category names to local icons', () => {
    expect(resolveCategoryIcon('Fruits & Vegetables')).toContain('vegetables-broccoli')
    expect(resolveCategoryIcon('Breads & Bakery')).toContain('bread-baguette')
    expect(resolveCategoryIcon('Beverages')).toContain('soft-drinks')
  })
})
