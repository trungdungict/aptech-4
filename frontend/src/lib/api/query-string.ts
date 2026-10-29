/** Build a query string from defined values only (no empty/null/undefined). */
export function toQueryString(
  params: Record<string, string | number | boolean | null | undefined>,
): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue
    }
    search.set(key, String(value))
  }
  const encoded = search.toString()
  return encoded.length > 0 ? `?${encoded}` : ''
}
