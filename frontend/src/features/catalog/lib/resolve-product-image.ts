import imageMapJson from '../data/foodmart-image-map.json'

const PLACEHOLDER = null

type ImageMapFile = Record<string, string>

const rawMap = imageMapJson as ImageMapFile

/** SKU → public asset path (entries starting with `_` are metadata). */
export const foodmartSkuImageMap: Readonly<Record<string, string>> = Object.fromEntries(
  Object.entries(rawMap).filter(([key, value]) => !key.startsWith('_') && typeof value === 'string'),
)

const NAME_KEYWORDS: Array<{ pattern: RegExp; path: string }> = [
  { pattern: /avocado/i, path: '/assets/foodmart/images/products/thumb-avocado.png' },
  { pattern: /banana/i, path: '/assets/foodmart/images/products/thumb-bananas.png' },
  { pattern: /raspberry|strawberry|berry|apple/i, path: '/assets/foodmart/images/products/thumb-raspberries.png' },
  { pattern: /tomato(?!.*ketchup)/i, path: '/assets/foodmart/images/products/thumb-tomatoes.png' },
  { pattern: /ketchup|sauce|pasta|spaghetti/i, path: '/assets/foodmart/images/products/thumb-tomatoketchup.png' },
  { pattern: /cucumber|broccoli|vegetable|veg/i, path: '/assets/foodmart/images/products/thumb-cucumber.png' },
  { pattern: /milk|yogurt|dairy/i, path: '/assets/foodmart/images/products/thumb-milk.png' },
  { pattern: /juice|melon|orange/i, path: '/assets/foodmart/images/products/thumb-orange-juice.png' },
  { pattern: /honey/i, path: '/assets/foodmart/images/products/thumb-honey.jpg' },
  { pattern: /herb|oil|olive/i, path: '/assets/foodmart/images/products/thumb-herb.jpg' },
  { pattern: /tuna|salmon|chicken|meat|seafood|fish/i, path: '/assets/foodmart/images/products/thumb-tuna.jpg' },
  { pattern: /biscuit|almond|cashew|nut|snack/i, path: '/assets/foodmart/images/products/thumb-biscuits.png' },
  { pattern: /bread|bakery|croissant|muffin/i, path: '/assets/foodmart/images/products/product-thumb-11.jpg' },
]

/**
 * Resolves a display image for a catalog product.
 * Prefers API `imageUrl` when the backend eventually stores media; otherwise uses
 * the local FoodMart SKU map / name heuristics. Never invents remote CDN URLs.
 */
export function resolveProductImage(input: {
  sku?: string | null
  name?: string | null
  imageUrl?: string | null
}): string | null {
  const apiUrl = input.imageUrl?.trim()
  if (apiUrl) {
    return apiUrl
  }

  const sku = input.sku?.trim()
  if (sku && foodmartSkuImageMap[sku]) {
    return foodmartSkuImageMap[sku]
  }

  const haystack = `${input.name ?? ''} ${sku ?? ''}`
  for (const entry of NAME_KEYWORDS) {
    if (entry.pattern.test(haystack)) {
      return entry.path
    }
  }

  return PLACEHOLDER
}

const CATEGORY_ICONS: Array<{ pattern: RegExp; path: string }> = [
  {
    pattern: /fruit|veg|produce|fresh/i,
    path: '/assets/foodmart/icons/icon-vegetables-broccoli.png',
  },
  {
    pattern: /bread|bakery|baked|sweet/i,
    path: '/assets/foodmart/icons/icon-bread-baguette.png',
  },
  {
    pattern: /drink|beverage|juice|soft/i,
    path: '/assets/foodmart/icons/icon-soft-drinks-bottle.png',
  },
  {
    pattern: /wine|alcohol|spirit/i,
    path: '/assets/foodmart/icons/icon-wine-glass-bottle.png',
  },
  {
    pattern: /meat|seafood|animal|chicken|fish/i,
    path: '/assets/foodmart/icons/icon-animal-products-drumsticks.png',
  },
  {
    pattern: /pantry|flour|cooking|herb|dairy|egg/i,
    path: '/assets/foodmart/icons/icon-bread-herb-flour.png',
  },
]

export function resolveCategoryIcon(categoryName: string): string {
  for (const entry of CATEGORY_ICONS) {
    if (entry.pattern.test(categoryName)) {
      return entry.path
    }
  }
  return '/assets/foodmart/icons/icon-vegetables-broccoli.png'
}
