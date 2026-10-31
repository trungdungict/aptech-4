import { describe, expect, it } from 'vitest'

import demo from './foodmart-demo-data.json'
import imageMapJson from './foodmart-image-map.json'

const imageMap = Object.fromEntries(
  Object.entries(imageMapJson).filter(([key]) => !key.startsWith('_')),
) as Record<string, string>

describe('foodmart-demo-data', () => {
  it('has 8 grocery categories and 40–50 products with unique SKUs', () => {
    expect(demo.categories).toHaveLength(8)
    expect(demo.products.length).toBeGreaterThanOrEqual(40)
    expect(demo.products.length).toBeLessThanOrEqual(50)

    const names = demo.categories.map((c) => c.name)
    expect(names).toEqual([
      'Fruits & Vegetables',
      'Breads & Bakery',
      'Dairy & Eggs',
      'Meat & Seafood',
      'Beverages',
      'Snacks & Nuts',
      'Pantry & Cooking',
      'Frozen Foods',
    ])

    const skus = demo.products.map((p) => p.sku)
    expect(new Set(skus).size).toBe(skus.length)
    expect(skus.every((sku) => sku.startsWith('FOOD-'))).toBe(true)
  })

  it('maps every product SKU to a local FoodMart public path (no unsupported fields)', () => {
    for (const product of demo.products) {
      const path = imageMap[product.sku]
      expect(path, `missing map for ${product.sku}`).toBeTruthy()
      expect(path).toBe(product.imagePath)
      expect(path.startsWith('/assets/foodmart/images/products/')).toBe(true)
      expect(product).not.toHaveProperty('brand')
      expect(product).not.toHaveProperty('rating')
      expect(product).not.toHaveProperty('stockQuantity')
      expect(product).not.toHaveProperty('imageUrl')
    }
  })
})
