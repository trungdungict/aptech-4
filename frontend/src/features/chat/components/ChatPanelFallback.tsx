/**
 * Lightweight Suspense fallback while the ChatPanel chunk loads.
 * Matches ChatPanel launcher position; no SSE or chat API work.
 */
export function ChatPanelFallback() {
  return (
    <div className="fixed right-4 bottom-4 z-50 flex flex-col items-end gap-2">
      <button
        type="button"
        className="rounded-full bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground shadow-lg opacity-80"
        disabled
        aria-busy="true"
        aria-label="Loading chat"
      >
        Loading chat…
      </button>
    </div>
  )
}
