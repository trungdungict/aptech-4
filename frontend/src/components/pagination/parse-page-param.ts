/** Reads a 0-based `page` query value (invalid → 0). */
export function parsePageParam(raw: string | null): number {
  const value = Number(raw ?? '0')
  return Number.isInteger(value) && value >= 0 ? value : 0
}

/** Writes 0-based page into search params; omits `page` when on the first page. */
export function writePageParam(params: URLSearchParams, page: number): URLSearchParams {
  const next = new URLSearchParams(params)
  if (page <= 0) {
    next.delete('page')
  } else {
    next.set('page', String(page))
  }
  return next
}

export function clampPage(page: number, totalPages: number): number {
  const maxIndex = Math.max(0, totalPages - 1)
  return Math.min(Math.max(0, page), maxIndex)
}
