/**
 * Lightweight Suspense fallback while the ChatPanel chunk loads.
 * Matches compact ChatPanel launcher footprint; no SSE or chat API work.
 */
export function ChatPanelFallback() {
  return (
    <div
      data-testid="chat-launcher-root"
      className="pointer-events-none fixed right-3 bottom-3 z-50 flex flex-col items-end gap-2 sm:right-4 sm:bottom-4"
    >
      <button
        type="button"
        className="pointer-events-auto flex size-12 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground shadow-lg opacity-80"
        disabled
        aria-busy="true"
        aria-label="Loading chat"
      >
        …
      </button>
    </div>
  )
}
