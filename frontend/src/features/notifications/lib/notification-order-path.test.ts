import { describe, expect, it } from 'vitest'

import { notificationOrderPath } from './notification-order-path'

describe('notificationOrderPath', () => {
  it('routes customers to storefront order detail', () => {
    expect(notificationOrderPath('abc', ['CUSTOMER'])).toBe('/orders/abc')
  })

  it('routes admins to admin order detail', () => {
    expect(notificationOrderPath('abc', ['ADMIN'])).toBe('/admin/orders/abc')
  })
})
