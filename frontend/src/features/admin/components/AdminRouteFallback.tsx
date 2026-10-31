/**
 * Shared Suspense fallback for Admin layout and page chunks.
 */
export function AdminRouteFallback() {
  return (
    <div className="px-4 py-10 md:px-0" role="status" aria-live="polite">
      <p className="text-sm text-muted-foreground">Loading admin…</p>
    </div>
  )
}
