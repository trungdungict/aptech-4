import { describe, expect, it } from 'vitest'

/** Guards against accidentally rendering raw tool/SQL failures in the chat bubble. */
function looksLikeInternalLeak(text: string): boolean {
  return /attentionCounts|ATTENTION_STATUSES|getSalesSummary|SQLException|org\.hibernate|stack trace/i.test(
    text,
  )
}

describe('chat reply safety', () => {
  it('accepts user-facing sales and order list prose', () => {
    expect(
      looksLikeInternalLeak(
        'Sales summary (last 30 days, UTC):\n- Revenue orders: 56\n- Revenue: 219737000.00 VND',
      ),
    ).toBe(false)
    expect(
      looksLikeInternalLeak(
        'Found 23 orders (status PAID). Showing 10 rows:\n1. … — PAID — 500000 VND',
      ),
    ).toBe(false)
  })

  it('flags internal identifiers and exception class names', () => {
    expect(looksLikeInternalLeak('Source: attentionCounts / ATTENTION_STATUSES')).toBe(true)
    expect(looksLikeInternalLeak('org.hibernate.exception.SQLGrammarException')).toBe(true)
  })
})
