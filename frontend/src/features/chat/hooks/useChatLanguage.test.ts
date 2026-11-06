import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'

import { CHAT_LANGUAGE_STORAGE_KEY } from '../types'
import { useChatLanguage } from './useChatLanguage'

describe('useChatLanguage (independent of app locale)', () => {
  afterEach(() => {
    localStorage.clear()
  })

  it('defaults to EN and keeps aptech4.chat.language storage', () => {
    const { result } = renderHook(() => useChatLanguage())
    expect(result.current.language).toBe('EN')

    act(() => {
      result.current.setLanguage('VI')
    })
    expect(result.current.language).toBe('VI')
    expect(localStorage.getItem(CHAT_LANGUAGE_STORAGE_KEY)).toBe('VI')
    expect(localStorage.getItem('aptech4.locale')).toBeNull()
  })
})
