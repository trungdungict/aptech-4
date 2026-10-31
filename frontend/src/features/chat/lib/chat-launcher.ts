/** CSS custom properties reserved so list pagination stays clear of the floating launcher. */
export const CHAT_LAUNCHER_CLEARANCE_INLINE = '--chat-launcher-clearance-inline'
export const CHAT_LAUNCHER_CLEARANCE_BLOCK = '--chat-launcher-clearance-block'

export const CHAT_LAUNCHER_MINIMIZED_KEY = 'aptech.chat.launcherMinimized'

export type ChatLauncherFootprint = 'hidden' | 'minimized' | 'compact' | 'panel'

export function chatLauncherClearance(footprint: ChatLauncherFootprint): {
  inline: string
  block: string
} {
  switch (footprint) {
    case 'hidden':
      return { inline: '0px', block: '0px' }
    case 'minimized':
      return { inline: '3.5rem', block: '4.25rem' }
    case 'panel':
      return { inline: '4.75rem', block: '5.5rem' }
    case 'compact':
    default:
      return { inline: '4.75rem', block: '5.25rem' }
  }
}

export function readLauncherMinimized(): boolean {
  try {
    return sessionStorage.getItem(CHAT_LAUNCHER_MINIMIZED_KEY) === '1'
  } catch {
    return false
  }
}

export function writeLauncherMinimized(minimized: boolean): void {
  try {
    if (minimized) {
      sessionStorage.setItem(CHAT_LAUNCHER_MINIMIZED_KEY, '1')
    } else {
      sessionStorage.removeItem(CHAT_LAUNCHER_MINIMIZED_KEY)
    }
  } catch {
    // Ignore quota / private-mode failures — in-memory state still works.
  }
}
