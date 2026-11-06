import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import {
  APP_CURRENCY_STORAGE_KEY,
  DEFAULT_APP_CURRENCY,
} from './currencies'
import { setActiveCurrency } from './currency-store'
import { formatDate, formatMoney, formatNumber } from './format'

describe('locale formatting', () => {
  beforeEach(() => {
    localStorage.removeItem(APP_CURRENCY_STORAGE_KEY)
    setActiveCurrency(DEFAULT_APP_CURRENCY)
  })

  afterEach(() => {
    localStorage.removeItem(APP_CURRENCY_STORAGE_KEY)
    setActiveCurrency(DEFAULT_APP_CURRENCY)
  })

  it('defaults to USD display for USD record amounts', () => {
    const usd = formatMoney(12.99, 'USD', { locale: 'en' })
    expect(usd).toMatch(/\$12\.99|12\.99/)
  })

  it('converts USD record amounts to VND with the documented mock rate', () => {
    setActiveCurrency('VND')
    const vnd = formatMoney(1.8, 'USD', { locale: 'vi', displayCurrency: 'VND' })
    // 1.80 × 25_000 = 45_000
    expect(vnd).toMatch(/45\.000|45,000/)
    expect(vnd).toMatch(/₫|VND/)
  })

  it('keeps language and currency independent', () => {
    const enUsd = formatMoney(12.99, 'USD', { locale: 'en', displayCurrency: 'USD' })
    const viUsd = formatMoney(12.99, 'USD', { locale: 'vi', displayCurrency: 'USD' })
    expect(enUsd).toMatch(/12\.99/)
    expect(viUsd).toMatch(/12[,.]99/)
    expect(enUsd).toMatch(/\$|USD/)
    expect(viUsd).toMatch(/\$|USD/)
  })

  it('formats numbers with locale grouping', () => {
    expect(formatNumber(12345.6, { locale: 'vi', maximumFractionDigits: 1 })).toMatch(/12\.345,6|12,345\.6/)
    expect(formatNumber(12345.6, { locale: 'en', maximumFractionDigits: 1 })).toBe('12,345.6')
  })

  it('formats dates for both locales', () => {
    const instant = '2026-11-02T08:00:00.000Z'
    const vi = formatDate(instant, { locale: 'vi', timeZone: 'UTC' })
    const en = formatDate(instant, { locale: 'en', timeZone: 'UTC' })
    expect(vi.length).toBeGreaterThan(4)
    expect(en.length).toBeGreaterThan(4)
    expect(vi).not.toEqual(en)
  })
})
