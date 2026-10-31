import type { PublicPlacement } from '../types'

export type PromotionNavTarget =
  | { kind: 'internal'; to: string }
  | { kind: 'external'; href: string }
  | { kind: 'none' }

/**
 * Maps public placement CTA fields to a safe navigation target.
 * Prefers server `cta.href`; falls back to `cta.url` for URL-type creatives.
 */
export function resolvePromotionCta(placement: Pick<PublicPlacement, 'cta'>): PromotionNavTarget {
  const href = placement.cta?.href?.trim() || placement.cta?.url?.trim() || ''
  if (!href) {
    return { kind: 'none' }
  }
  if (href.startsWith('/') && !href.startsWith('//')) {
    return { kind: 'internal', to: href }
  }
  try {
    const parsed = new URL(href)
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
      return { kind: 'external', href }
    }
  } catch {
    return { kind: 'none' }
  }
  return { kind: 'none' }
}

export function promotionCtaLabel(placement: Pick<PublicPlacement, 'ctaLabel'>, fallback = 'Shop now'): string {
  const label = placement.ctaLabel?.trim()
  return label && label.length > 0 ? label : fallback
}
