import { afterEach, describe, expect, it } from 'vitest'

import {
  APP_LOCALES,
  APP_LOCALE_OPTIONS,
  APP_LOCALE_STORAGE_KEY,
  DEFAULT_APP_LOCALE,
  getAppLocaleOption,
  isAppLocale,
  parseAppLocale,
  toIntlLocale,
} from './locales'
import { readStoredAppLocale, writeStoredAppLocale } from './storage'

describe('app locales', () => {
  afterEach(() => {
    localStorage.clear()
  })

  it('exposes extensible locale options with native labels', () => {
    expect(APP_LOCALE_OPTIONS.map((o) => o.code)).toEqual([...APP_LOCALES])
    expect(APP_LOCALE_OPTIONS).toEqual(
      expect.arrayContaining([
        { code: 'vi', nativeLabel: 'Tiếng Việt' },
        { code: 'en', nativeLabel: 'English' },
      ]),
    )
    expect(getAppLocaleOption('en').nativeLabel).toBe('English')
  })

  it('defaults to vi', () => {
    expect(DEFAULT_APP_LOCALE).toBe('vi')
    expect(parseAppLocale(undefined)).toBe('vi')
    expect(parseAppLocale('fr')).toBe('vi')
    expect(isAppLocale('en')).toBe(true)
    expect(isAppLocale('EN')).toBe(false)
  })

  it('maps Intl tags', () => {
    expect(toIntlLocale('vi')).toBe('vi-VN')
    expect(toIntlLocale('en')).toBe('en-US')
  })

  it('persists valid locale and falls back for invalid', () => {
    writeStoredAppLocale('en')
    expect(localStorage.getItem(APP_LOCALE_STORAGE_KEY)).toBe('en')
    expect(readStoredAppLocale()).toBe('en')

    localStorage.setItem(APP_LOCALE_STORAGE_KEY, 'fr-FR')
    expect(readStoredAppLocale()).toBe('vi')
  })
})
