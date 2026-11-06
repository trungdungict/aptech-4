import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { changeAppLocale, i18n, initI18n } from './i18n'
import { getActiveLocale } from './locale-store'
import { APP_LOCALE_STORAGE_KEY } from './locales'

describe('i18n foundation', () => {
  beforeEach(async () => {
    localStorage.clear()
    document.documentElement.lang = ''
    await initI18n()
    await changeAppLocale('vi')
  })

  afterEach(() => {
    localStorage.clear()
  })

  it('initializes with default vi', async () => {
    expect(i18n.language).toMatch(/^vi/)
    expect(getActiveLocale()).toBe('vi')
    expect(document.documentElement.lang).toBe('vi')
    expect(i18n.t('language.label')).toBe('Ngôn ngữ')
  })

  it('switches to en and back to vi', async () => {
    await changeAppLocale('en')
    expect(getActiveLocale()).toBe('en')
    expect(document.documentElement.lang).toBe('en')
    expect(localStorage.getItem(APP_LOCALE_STORAGE_KEY)).toBe('en')
    expect(i18n.t('language.label')).toBe('Language')

    await changeAppLocale('vi')
    expect(getActiveLocale()).toBe('vi')
    expect(document.documentElement.lang).toBe('vi')
    expect(i18n.t('nav.catalog')).toBe('Danh mục')
  })

  it('restores persisted locale on init', async () => {
    localStorage.setItem(APP_LOCALE_STORAGE_KEY, 'en')
    await initI18n()
    expect(getActiveLocale()).toBe('en')
    expect(document.documentElement.lang).toBe('en')
  })
})
