import { useEffect } from 'react'

import {
  CHAT_LAUNCHER_CLEARANCE_BLOCK,
  CHAT_LAUNCHER_CLEARANCE_INLINE,
  chatLauncherClearance,
  type ChatLauncherFootprint,
} from '../lib/chat-launcher'

/** Publishes document-level safe-area offsets for shared pagination / list footers. */
export function useChatLauncherClearance(footprint: ChatLauncherFootprint): void {
  useEffect(() => {
    const root = document.documentElement
    const { inline, block } = chatLauncherClearance(footprint)
    root.style.setProperty(CHAT_LAUNCHER_CLEARANCE_INLINE, inline)
    root.style.setProperty(CHAT_LAUNCHER_CLEARANCE_BLOCK, block)
    return () => {
      root.style.removeProperty(CHAT_LAUNCHER_CLEARANCE_INLINE)
      root.style.removeProperty(CHAT_LAUNCHER_CLEARANCE_BLOCK)
    }
  }, [footprint])
}
