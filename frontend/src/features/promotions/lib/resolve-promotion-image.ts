/**
 * Resolves a placement image for display.
 * Accepts path-absolute upload paths (`/uploads/promotions/…`) or http(s) URLs.
 * Does not invent CDN hosts or rewrite relative API base segments.
 */
export function resolvePromotionImageUrl(
  desktopImageUrl?: string | null,
  mobileImageUrl?: string | null,
  preferMobile = false,
): string | null {
  const primary = preferMobile
    ? firstUsable(mobileImageUrl) ?? firstUsable(desktopImageUrl)
    : firstUsable(desktopImageUrl) ?? firstUsable(mobileImageUrl)
  return primary
}

function firstUsable(raw?: string | null): string | null {
  if (raw == null) {
    return null
  }
  const value = raw.trim()
  if (!value) {
    return null
  }
  if (value.startsWith('/') && !value.startsWith('//')) {
    if (value.includes('://') || value.includes('..')) {
      return null
    }
    return value
  }
  try {
    const parsed = new URL(value)
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
      return value
    }
  } catch {
    return null
  }
  return null
}
