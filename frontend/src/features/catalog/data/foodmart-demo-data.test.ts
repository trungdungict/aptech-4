import { describe, expect, it } from 'vitest'

import demo from './foodmart-demo-data.json'
import imageMapJson from './foodmart-image-map.json'

const imageMap = Object.fromEntries(
  Object.entries(imageMapJson).filter(([key]) => !key.startsWith('_')),
) as Record<string, string>

const FALLBACK_MISSING_EN_SKU = 'FOOD-FROZEN-PEAS-001'

describe('foodmart-demo-data', () => {
  it('has 8 grocery categories and 40–50 products with unique SKUs (VI canonical)', () => {
    expect(demo.categories).toHaveLength(8)
    expect(demo.products.length).toBeGreaterThanOrEqual(40)
    expect(demo.products.length).toBeLessThanOrEqual(50)
    expect(demo._meta.canonicalBaseLocale).toBe('vi')
    expect(demo._meta.fallbackMissingEnSku).toBe(FALLBACK_MISSING_EN_SKU)

    const names = demo.categories.map((c) => c.name)
    expect(names).toEqual([
      'Rau củ & Trái cây',
      'Bánh mì & Tiệm bánh',
      'Sữa & Trứng',
      'Thịt & Hải sản',
      'Đồ uống',
      'Đồ ăn vặt & Hạt',
      'Gia vị & Nấu ăn',
      'Thực phẩm đông lạnh',
    ])
    expect(demo.categories.every((c) => Boolean(c.nameEn))).toBe(true)

    const skus = demo.products.map((p) => p.sku)
    expect(new Set(skus).size).toBe(skus.length)
    expect(skus.every((sku) => sku.startsWith('FOOD-'))).toBe(true)
  })

  it('keeps bilingual EN fields except the intentional missing-EN fixture', () => {
    const peas = demo.products.find((p) => p.sku === FALLBACK_MISSING_EN_SKU)
    expect(peas).toBeDefined()
    expect(peas?.omitEnglishTranslation).toBe(true)
    expect(peas?.nameEn).toBeUndefined()

    const withEn = demo.products.filter((p) => p.sku !== FALLBACK_MISSING_EN_SKU)
    expect(withEn.every((p) => Boolean(p.nameEn))).toBe(true)
    expect(withEn.every((p) => !p.omitEnglishTranslation)).toBe(true)
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
