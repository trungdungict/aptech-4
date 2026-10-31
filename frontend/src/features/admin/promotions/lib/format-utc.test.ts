import { describe, expect, it } from 'vitest'

import { formatUtcInstant, isValidUtcInstantInput, toApiUtcInstant } from './format-utc'

describe('format-utc', () => {
  it('formats valid instants as ISO UTC with Z', () => {
    expect(formatUtcInstant('2026-11-01T00:00:00Z')).toBe('2026-11-01T00:00:00.000Z')
    expect(formatUtcInstant(null)).toBe('—')
  })

  it('requires Z or numeric offset for API-safe inputs', () => {
    expect(isValidUtcInstantInput('2026-11-01T00:00:00Z')).toBe(true)
    expect(isValidUtcInstantInput('2026-11-01T07:00:00+07:00')).toBe(true)
    expect(isValidUtcInstantInput('2026-11-01T00:00:00')).toBe(false)
    expect(isValidUtcInstantInput('')).toBe(false)
  })

  it('normalizes to API ISO UTC', () => {
    expect(toApiUtcInstant('2026-11-01T00:00:00Z')).toBe('2026-11-01T00:00:00.000Z')
  })
})
