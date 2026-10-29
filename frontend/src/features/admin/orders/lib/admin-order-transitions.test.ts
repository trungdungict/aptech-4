import { describe, expect, it } from 'vitest'

import { allowedAdminTransitions } from './admin-order-transitions'

describe('allowedAdminTransitions', () => {
  it('mirrors ADR-008 admin whitelist', () => {
    expect(allowedAdminTransitions('PAID')).toEqual(['PROCESSING', 'CANCELLED'])
    expect(allowedAdminTransitions('PROCESSING')).toEqual(['SHIPPED', 'CANCELLED'])
    expect(allowedAdminTransitions('SHIPPED')).toEqual(['COMPLETED'])
    expect(allowedAdminTransitions('PENDING_PAYMENT')).toEqual(['CANCELLED'])
    expect(allowedAdminTransitions('COMPLETED')).toEqual([])
    expect(allowedAdminTransitions('FAILED')).toEqual([])
  })

  it('does not offer payment-owned targets', () => {
    for (const from of ['PAID', 'PROCESSING', 'PENDING_PAYMENT', 'SHIPPED']) {
      expect(allowedAdminTransitions(from)).not.toContain('PAID')
      expect(allowedAdminTransitions(from)).not.toContain('FAILED')
    }
  })
})
