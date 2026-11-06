import type { AppLocale } from '@/lib/i18n'

import type { ProductListParams } from '../types'

/** Catalog payloads include localized name/description — locale is part of the cache key. */
export const catalogQueryKeys = {
  all: ['catalog'] as const,
  categories: (locale: AppLocale) => [...catalogQueryKeys.all, 'categories', locale] as const,
  products: (locale: AppLocale) => [...catalogQueryKeys.all, 'products', locale] as const,
  productList: (locale: AppLocale, params: ProductListParams) =>
    [...catalogQueryKeys.products(locale), 'list', params] as const,
  productDetail: (locale: AppLocale, id: string) =>
    [...catalogQueryKeys.products(locale), 'detail', id] as const,
}
