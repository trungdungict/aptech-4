import type { AppLocale } from '@/lib/i18n'

/** Strategy/campaign/placement display names follow Accept-Language. */
export const adminPromotionQueryKeys = {
  all: ['admin', 'promotions'] as const,
  strategies: (locale: AppLocale) =>
    [...adminPromotionQueryKeys.all, 'strategies', locale] as const,
  strategy: (locale: AppLocale, id: string) =>
    [...adminPromotionQueryKeys.strategies(locale), id] as const,
  campaigns: (locale: AppLocale) =>
    [...adminPromotionQueryKeys.all, 'campaigns', locale] as const,
  campaign: (locale: AppLocale, id: string) =>
    [...adminPromotionQueryKeys.campaigns(locale), id] as const,
  placements: (locale: AppLocale, campaignId: string) =>
    [...adminPromotionQueryKeys.campaign(locale, campaignId), 'placements'] as const,
  placement: (locale: AppLocale, id: string) =>
    [...adminPromotionQueryKeys.all, 'placement', locale, id] as const,
}
