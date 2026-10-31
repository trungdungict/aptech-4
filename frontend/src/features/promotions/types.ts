/** Mirrors backend `PublicPlacementResponse` / `HomepagePromotionsResponse` (P19). */

export type PromotionPlacementKey = 'HOME_HERO' | 'HOME_PROMO_TOP' | 'HOME_PROMO_BOTTOM'

export type PromotionCtaType = 'PRODUCT' | 'CATEGORY' | 'COLLECTION' | 'URL' | 'NONE'

export type PublicPromotionCta = {
  type: PromotionCtaType
  productId: string | null
  categoryId: string | null
  collectionKey: string | null
  url: string | null
  /** Server-built path or absolute URL for navigation. */
  href: string | null
}

export type PublicPlacement = {
  id: string
  campaignId: string
  placementKey: PromotionPlacementKey
  title: string
  subtitle: string | null
  ctaLabel: string | null
  cta: PublicPromotionCta | null
  /** Path-absolute upload (`/uploads/promotions/…`) or absolute URL — never invent hosts. */
  desktopImageUrl: string
  mobileImageUrl: string | null
  displayOrder: number
}

export type HomepagePromotions = {
  hero: PublicPlacement[]
  promoTop: PublicPlacement | null
  promoBottom: PublicPlacement | null
}
