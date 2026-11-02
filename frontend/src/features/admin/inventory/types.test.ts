import { describe, expect, it } from 'vitest'

import { resolveStockBucket } from './types'

describe('resolveStockBucket', () => {
  it('matches backend LOW / OUT / IN_STOCK boundaries for threshold 5', () => {
    expect(resolveStockBucket(0, 5)).toBe('OUT')
    expect(resolveStockBucket(-1, 5)).toBe('OUT')
    expect(resolveStockBucket(1, 5)).toBe('LOW')
    expect(resolveStockBucket(5, 5)).toBe('LOW')
    expect(resolveStockBucket(6, 5)).toBe('IN_STOCK')
  })
})
