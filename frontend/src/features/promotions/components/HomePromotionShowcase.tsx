import { useQuery } from '@tanstack/react-query'

import { Skeleton } from '@/components/ui/skeleton'

import { getHomepagePromotions } from '../api/promotions-api'
import { promotionQueryKeys } from '../hooks/promotion-query-keys'
import {
  FOODMART_HOMEPAGE_FALLBACK,
  withFoodmartHomepageFallback,
} from '../lib/foodmart-fallback'
import { HomeHeroCarousel } from './HomeHeroCarousel'
import { HomePromoStack } from './HomePromoStack'

/**
 * Grocery-style homepage band: large left hero carousel + right stacked promos.
 * Empty/error/malformed API → static FoodMart artwork (ADR-020 R3).
 */
export function HomePromotionShowcase() {
  const query = useQuery({
    queryKey: promotionQueryKeys.homepage(),
    queryFn: ({ signal }) => getHomepagePromotions(signal),
    staleTime: 30_000,
    retry: false,
  })

  if (query.isLoading) {
    return (
      <section
        className="grid gap-4 lg:grid-cols-[1.55fr_1fr] lg:gap-5"
        aria-busy="true"
        aria-label="Loading promotions"
      >
        <Skeleton className="min-h-[16rem] w-full rounded-xl lg:min-h-[22rem]" />
        <div className="grid gap-4">
          <Skeleton className="min-h-[9.5rem] w-full rounded-xl" />
          <Skeleton className="min-h-[9.5rem] w-full rounded-xl" />
        </div>
      </section>
    )
  }

  const data = query.isError
    ? FOODMART_HOMEPAGE_FALLBACK
    : withFoodmartHomepageFallback(query.data)

  return (
    <section
      className="grid gap-4 lg:grid-cols-[1.55fr_1fr] lg:items-stretch lg:gap-5"
      aria-labelledby="home-placements-heading"
    >
      <div className="space-y-2">
        <div className="flex flex-wrap items-end justify-between gap-2">
          <div>
            <h2 id="home-placements-heading" className="text-2xl font-semibold text-foreground">
              Featured offers
            </h2>
            <p className="text-sm text-muted-foreground">
              {query.isError
                ? 'Showing FoodMart artwork while promotions are unavailable.'
                : 'Live placements from the store API — FoodMart artwork when a slot is empty.'}
            </p>
          </div>
        </div>
        <HomeHeroCarousel slides={data.hero} />
      </div>
      <HomePromoStack promoTop={data.promoTop} promoBottom={data.promoBottom} className="lg:pt-12" />
    </section>
  )
}
