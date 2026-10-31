/**
 * Display Instant strings as explicit UTC without silent local reinterpretation.
 * Backend timestamps are ISO-8601 with Z / offset; we normalize display to `…Z`.
 */
export function formatUtcInstant(value: string | null | undefined): string {
  if (!value || !value.trim()) {
    return '—'
  }
  const trimmed = value.trim()
  const date = new Date(trimmed)
  if (Number.isNaN(date.getTime())) {
    return trimmed
  }
  return date.toISOString()
}

/** True when the string parses as a valid Instant (UTC-safe for API submission). */
export function isValidUtcInstantInput(value: string): boolean {
  const trimmed = value.trim()
  if (!trimmed) {
    return false
  }
  // Prefer explicit Z or numeric offset so local-only strings are not silently accepted.
  if (!/(Z|[+-]\d{2}:\d{2})$/i.test(trimmed)) {
    return false
  }
  const date = new Date(trimmed)
  return !Number.isNaN(date.getTime())
}

/** Normalize to ISO-8601 UTC for API payloads. */
export function toApiUtcInstant(value: string): string {
  return new Date(value.trim()).toISOString()
}
