export const promotionQueryKeys = {
  all: ['promotions'] as const,
  homepage: () => [...promotionQueryKeys.all, 'homepage'] as const,
  placements: (keys?: string[]) =>
    [...promotionQueryKeys.all, 'placements', keys?.slice().sort().join(',') ?? 'ALL'] as const,
}
