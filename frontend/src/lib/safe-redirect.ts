/**
 * Allow only same-origin relative paths (open-redirect safe).
 * Rejects protocol-relative URLs, absolute URLs, and empty values.
 */
export function safeInternalPath(
  raw: string | null | undefined,
  fallback = '/',
): string {
  if (raw == null || raw === '') {
    return fallback
  }
  if (!raw.startsWith('/') || raw.startsWith('//') || raw.includes('://')) {
    return fallback
  }
  return raw
}
