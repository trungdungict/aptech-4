/** Admin promotion DTOs mirroring P19 backend contracts. */

export type PromotionStrategyType =
  | 'PERCENTAGE_DISCOUNT'
  | 'FIXED_AMOUNT_DISCOUNT'
  | 'PRODUCT_DISCOUNT'
  | 'CATEGORY_DISCOUNT'
  | 'BANNER_ONLY'

export type PromotionCampaignStatus = 'DRAFT' | 'SCHEDULED' | 'ACTIVE' | 'PAUSED' | 'EXPIRED'

export type PromotionPlacementKey = 'HOME_HERO' | 'HOME_PROMO_TOP' | 'HOME_PROMO_BOTTOM'

export type PromotionCtaType = 'PRODUCT' | 'CATEGORY' | 'COLLECTION' | 'URL' | 'NONE'

export type AdminPromotionStrategy = {
  id: string
  name: string
  code: string | null
  type: PromotionStrategyType
  discountPercent: number | null
  discountAmount: number | null
  maxDiscountAmount: number | null
  minOrderAmount: number | null
  minQuantity: number | null
  stackable: boolean
  priority: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export type AdminPromotionStrategyWrite = {
  name: string
  code?: string | null
  type: PromotionStrategyType
  discountPercent?: number | null
  discountAmount?: number | null
  maxDiscountAmount?: number | null
  minOrderAmount?: number | null
  minQuantity?: number | null
  stackable?: boolean
  priority?: number
  active?: boolean
}

export type AdminCampaignStrategySummary = {
  id: string
  name: string
  code: string | null
  type: string
  active: boolean
}

export type AdminPromotionCampaign = {
  id: string
  name: string
  slug: string
  status: PromotionCampaignStatus
  effectiveStatus: PromotionCampaignStatus
  pricingEffective: boolean
  placementEffective: boolean
  startsAt: string
  endsAt: string
  priority: number
  stackable: boolean
  notes: string | null
  strategy: AdminCampaignStrategySummary
  productIds: string[]
  categoryIds: string[]
  placementCount: number
  createdAt: string
  updatedAt: string
}

export type AdminPromotionCampaignWrite = {
  strategyId: string
  name: string
  slug?: string | null
  startsAt: string
  endsAt: string
  priority?: number | null
  stackable?: boolean | null
  notes?: string | null
  productIds?: string[] | null
  categoryIds?: string[] | null
}

export type AdminPromotionEligibilityWrite = {
  productIds: string[]
  categoryIds: string[]
}

export type AdminPromotionPlacement = {
  id: string
  campaignId: string
  placementKey: PromotionPlacementKey
  title: string
  subtitle: string | null
  ctaLabel: string | null
  ctaType: PromotionCtaType
  ctaProductId: string | null
  ctaCategoryId: string | null
  ctaCollectionKey: string | null
  ctaUrl: string | null
  desktopImageUrl: string
  mobileImageUrl: string | null
  displayOrder: number
  startsAt: string | null
  endsAt: string | null
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export type AdminPromotionPlacementWrite = {
  placementKey: PromotionPlacementKey
  title: string
  subtitle?: string | null
  ctaLabel?: string | null
  ctaType?: PromotionCtaType | null
  ctaProductId?: string | null
  ctaCategoryId?: string | null
  ctaCollectionKey?: string | null
  ctaUrl?: string | null
  desktopImageUrl: string
  mobileImageUrl?: string | null
  displayOrder?: number | null
  startsAt?: string | null
  endsAt?: string | null
  enabled?: boolean | null
}

export type AdminCampaignPreview = {
  evaluatedAt: string
  status: PromotionCampaignStatus
  effectiveStatus: PromotionCampaignStatus
  pricingEffective: boolean
  placementEffective: boolean
  note: string
}

export type AdminImageUploadResult = {
  imageUrl: string
  contentType: string
  sizeBytes: number
}

export const STRATEGY_TYPES = [
  'PERCENTAGE_DISCOUNT',
  'FIXED_AMOUNT_DISCOUNT',
  'PRODUCT_DISCOUNT',
  'CATEGORY_DISCOUNT',
  'BANNER_ONLY',
] as const satisfies readonly PromotionStrategyType[]

export const PLACEMENT_KEYS = [
  'HOME_HERO',
  'HOME_PROMO_TOP',
  'HOME_PROMO_BOTTOM',
] as const satisfies readonly PromotionPlacementKey[]

export const CTA_TYPES = ['NONE', 'PRODUCT', 'CATEGORY', 'COLLECTION', 'URL'] as const satisfies readonly PromotionCtaType[]
