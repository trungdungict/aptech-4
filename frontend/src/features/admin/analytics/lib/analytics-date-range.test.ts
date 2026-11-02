import { describe, expect, it } from 'vitest'

import {
  buildAppliedInstantsFromDateInputs,
  exclusiveToFromInclusiveEndDate,
  inclusiveEndFromExclusiveTo,
  instantToUtcDateInput,
  presetRangeInstants,
  utcDayStartIso,
  validateInclusiveUtcRange,
} from './analytics-date-range'

describe('analytics-date-range helpers', () => {
  it('builds UTC day start and exclusive to from inclusive end', () => {
    expect(utcDayStartIso('2026-06-01')).toBe('2026-06-01T00:00:00.000Z')
    expect(exclusiveToFromInclusiveEndDate('2026-06-04')).toBe('2026-06-05T00:00:00.000Z')
    expect(exclusiveToFromInclusiveEndDate('2026-01-31')).toBe('2026-02-01T00:00:00.000Z')
  })

  it('maps Instants back to date inputs with exclusive to semantics', () => {
    expect(instantToUtcDateInput('2026-06-01T00:00:00Z')).toBe('2026-06-01')
    expect(inclusiveEndFromExclusiveTo('2026-06-05T00:00:00.000Z')).toBe('2026-06-04')
  })

  it('validates inverted and overlong inclusive ranges', () => {
    expect(validateInclusiveUtcRange('2026-06-05', '2026-06-01')).toMatch(/on or before/i)
    expect(validateInclusiveUtcRange('2026-01-01', '2027-12-31')).toMatch(/366/)
    expect(validateInclusiveUtcRange('2026-06-01', '2026-06-04')).toBeNull()
  })

  it('buildAppliedInstantsFromDateInputs returns exclusive Instant pair', () => {
    expect(buildAppliedInstantsFromDateInputs('2026-06-01', '2026-06-04')).toEqual({
      from: '2026-06-01T00:00:00.000Z',
      to: '2026-06-05T00:00:00.000Z',
    })
    expect(buildAppliedInstantsFromDateInputs('2026-06-05', '2026-06-01')).toEqual({
      error: 'From date must be on or before end date',
    })
  })

  it('presetRangeInstants uses browser clock span', () => {
    const now = new Date('2026-06-10T12:00:00.000Z')
    const range = presetRangeInstants(30, now)
    expect(range.to).toBe(now.toISOString())
    expect(Date.parse(range.to) - Date.parse(range.from)).toBe(30 * 86_400_000)
  })
})
