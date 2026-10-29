import { describe, expect, it } from 'vitest'

import { safeInternalPath } from './safe-redirect'

describe('safeInternalPath', () => {
  it('allows relative app paths', () => {
    expect(safeInternalPath('/cart')).toBe('/cart')
    expect(safeInternalPath('/orders/abc?x=1')).toBe('/orders/abc?x=1')
  })

  it('rejects open redirects', () => {
    expect(safeInternalPath('https://evil.example')).toBe('/')
    expect(safeInternalPath('//evil.example')).toBe('/')
    expect(safeInternalPath('javascript:alert(1)')).toBe('/')
    expect(safeInternalPath(null, '/products')).toBe('/products')
  })
})
