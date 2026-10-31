import { describe, expect, it } from 'vitest'

import { adminInventoryFormSchema } from './schemas'

describe('adminInventoryFormSchema', () => {
  it('accepts non-negative integers', () => {
    expect(adminInventoryFormSchema.parse({ quantityOnHand: '0' })).toEqual({
      quantityOnHand: 0,
    })
    expect(adminInventoryFormSchema.parse({ quantityOnHand: '42' })).toEqual({
      quantityOnHand: 42,
    })
  })

  it('rejects empty, negative, and non-integer values', () => {
    expect(adminInventoryFormSchema.safeParse({ quantityOnHand: '' }).success).toBe(false)
    expect(adminInventoryFormSchema.safeParse({ quantityOnHand: '-1' }).success).toBe(false)
    expect(adminInventoryFormSchema.safeParse({ quantityOnHand: '1.5' }).success).toBe(false)
    expect(adminInventoryFormSchema.safeParse({ quantityOnHand: 'abc' }).success).toBe(false)

    const negative = adminInventoryFormSchema.safeParse({ quantityOnHand: '-3' })
    expect(negative.success).toBe(false)
    if (!negative.success) {
      expect(negative.error.issues[0]?.message).toMatch(/non-negative integer/i)
    }
  })
})
