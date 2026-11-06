import type { AppLocale } from '@/lib/i18n'

/** Public promotion placements include localized title/subtitle/cta — locale in key. */
export const promotionQueryKeys = {
  all: ['promotions'] as const,
  homepage: (locale: AppLocale) => [...promotionQueryKeys.all, 'homepage', locale] as const,
  placements: (locale: AppLocale, keys?: string[]) =>
    [
      ...promotionQueryKeys.all,
      'placements',
      locale,
      keys?.slice().sort().join(',') ?? 'ALL',
    ] as const,
}
