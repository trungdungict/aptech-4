import { lazy, Suspense } from 'react'

import { ChatPanelFallback } from './ChatPanelFallback'

const ChatPanel = lazy(async () => {
  const module = await import('./ChatPanel')
  return { default: module.ChatPanel }
})

type LazyChatPanelProps = {
  variant: 'customer' | 'admin'
}

/**
 * Code-splits ChatPanel (+ SSE client) out of the initial entry chunk.
 * Mount only for authenticated users (same as eager ChatPanel before Phase 7).
 * SSE still starts only after the user opens chat and sends a message.
 */
export function LazyChatPanel({ variant }: LazyChatPanelProps) {
  return (
    <Suspense fallback={<ChatPanelFallback />}>
      <ChatPanel variant={variant} />
    </Suspense>
  )
}
