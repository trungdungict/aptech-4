import type { ProductListParams } from '../types'

export const catalogQueryKeys = {
  all: ['catalog'] as const,
  categories: () => [...catalogQueryKeys.all, 'categories'] as const,
  products: () => [...catalogQueryKeys.all, 'products'] as const,
  productList: (params: ProductListParams) =>
    [...catalogQueryKeys.products(), 'list', params] as const,
  productDetail: (id: string) => [...catalogQueryKeys.products(), 'detail', id] as const,
}
