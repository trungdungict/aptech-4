export const adminPromotionQueryKeys = {
  all: ['admin', 'promotions'] as const,
  strategies: () => [...adminPromotionQueryKeys.all, 'strategies'] as const,
  strategy: (id: string) => [...adminPromotionQueryKeys.strategies(), id] as const,
  campaigns: () => [...adminPromotionQueryKeys.all, 'campaigns'] as const,
  campaign: (id: string) => [...adminPromotionQueryKeys.campaigns(), id] as const,
  placements: (campaignId: string) =>
    [...adminPromotionQueryKeys.campaign(campaignId), 'placements'] as const,
  placement: (id: string) => [...adminPromotionQueryKeys.all, 'placement', id] as const,
}
