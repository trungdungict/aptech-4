import { describe, expect, it } from 'vitest'

import { hasAdminRole } from './roles'

describe('hasAdminRole', () => {
  it('returns true only when ADMIN is present', () => {
    expect(hasAdminRole(['ADMIN'])).toBe(true)
    expect(hasAdminRole(['CUSTOMER', 'ADMIN'])).toBe(true)
  })

  it('fails closed for customer, empty, missing, or unknown values', () => {
    expect(hasAdminRole(['CUSTOMER'])).toBe(false)
    expect(hasAdminRole([])).toBe(false)
    expect(hasAdminRole(null)).toBe(false)
    expect(hasAdminRole(undefined)).toBe(false)
    expect(hasAdminRole(['ROLE_ADMIN'])).toBe(false)
    expect(hasAdminRole(['admin'])).toBe(false)
  })
})
