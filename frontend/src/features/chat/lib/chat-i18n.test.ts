import { describe, expect, it } from 'vitest'

import { chatCopy, defaultSuggestedPrompts } from './chat-i18n'

describe('chat-i18n', () => {
  it('defaults English copy and greeting', () => {
    expect(chatCopy('EN').greeting).toMatch(/Hello/i)
    expect(chatCopy('EN').title).toMatch(/Assistant/i)
  })

  it('provides Vietnamese copy', () => {
    expect(chatCopy('VI').greeting).toMatch(/Xin chào/i)
    expect(defaultSuggestedPrompts('VI', false)[0]).toMatch(/Đơn hàng/i)
  })

  it('admin prompts differ from customer', () => {
    expect(defaultSuggestedPrompts('EN', true)[0]).toMatch(/orders need processing/i)
    expect(defaultSuggestedPrompts('EN', false)[0]).toMatch(/latest order/i)
  })
})
