import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiClientError } from '@/lib/api/api-error'

import { getProduct, listProducts, normalizeListParams } from './catalog-api'

describe('catalog-api', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('normalizes list params to backend defaults and whitelist', () => {
    expect(normalizeListParams({})).toEqual({
      q: undefined,
      categoryId: undefined,
      minPrice: undefined,
      maxPrice: undefined,
      inStock: undefined,
      page: 0,
      size: 20,
      sort: 'name,asc',
    })
    expect(normalizeListParams({ sort: 'hacked,asc', size: 500, page: -2 }).sort).toBe('name,asc')
    expect(normalizeListParams({ sort: 'price,desc', size: 500 }).size).toBe(100)
  })

  it('serializes listing query parameters for GET /products', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          items: [],
          page: 1,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: false,
          last: true,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await listProducts({
      q: 'phone',
      categoryId: '11111111-1111-1111-1111-111111111111',
      minPrice: '1000',
      maxPrice: '5000',
      inStock: true,
      page: 1,
      size: 20,
      sort: 'price,desc',
    })

    expect(fetch).toHaveBeenCalledTimes(1)
    const url = String(vi.mocked(fetch).mock.calls[0]?.[0])
    expect(url).toContain('/products?')
    expect(url).toContain('q=phone')
    expect(url).toContain('categoryId=11111111-1111-1111-1111-111111111111')
    expect(url).toContain('minPrice=1000')
    expect(url).toContain('maxPrice=5000')
    expect(url).toContain('inStock=true')
    expect(url).toContain('page=1')
    expect(url).toContain('size=20')
    expect(url).toContain('sort=price%2Cdesc')
  })

  it('requests product detail by id', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          id: '22222222-2222-2222-2222-222222222222',
          sku: 'SKU-1',
          name: 'Demo',
          description: null,
          unitPrice: 1000,
          currency: 'VND',
          categoryId: '11111111-1111-1111-1111-111111111111',
          categoryName: 'Electronics',
          active: true,
          imageUrl: null,
          inStock: true,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await getProduct('22222222-2222-2222-2222-222222222222')
    expect(String(vi.mocked(fetch).mock.calls[0]?.[0])).toContain(
      '/products/22222222-2222-2222-2222-222222222222',
    )
  })

  it('propagates API errors instead of fabricating success', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 404,
          error: 'Not Found',
          message: 'Product not found',
          path: '/api/v1/products/x',
          details: [],
        }),
        { status: 404, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    await expect(getProduct('22222222-2222-2222-2222-222222222222')).rejects.toBeInstanceOf(
      ApiClientError,
    )
  })
})
