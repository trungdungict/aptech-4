import { afterEach, describe, expect, it } from 'vitest'

import {
  CHAT_LAUNCHER_MINIMIZED_KEY,
  chatLauncherClearance,
  readLauncherMinimized,
  writeLauncherMinimized,
} from './chat-launcher'

describe('chatLauncherClearance', () => {
  it('reserves less space when minimized than when compact', () => {
    const minimized = chatLauncherClearance('minimized')
    const compact = chatLauncherClearance('compact')
    expect(Number.parseFloat(minimized.inline)).toBeLessThan(Number.parseFloat(compact.inline))
    expect(chatLauncherClearance('hidden')).toEqual({ inline: '0px', block: '0px' })
  })
})

describe('launcher minimized persistence', () => {
  afterEach(() => {
    sessionStorage.clear()
  })

  it('reads and writes sessionStorage', () => {
    expect(readLauncherMinimized()).toBe(false)
    writeLauncherMinimized(true)
    expect(sessionStorage.getItem(CHAT_LAUNCHER_MINIMIZED_KEY)).toBe('1')
    expect(readLauncherMinimized()).toBe(true)
    writeLauncherMinimized(false)
    expect(readLauncherMinimized()).toBe(false)
  })
})
