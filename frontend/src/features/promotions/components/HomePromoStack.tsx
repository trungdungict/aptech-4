import { cn } from '@/lib/utils'

import type { PublicPlacement } from '../types'
import { PromoBannerCard } from './PromoBannerCard'

type HomePromoStackProps = {
  promoTop: PublicPlacement | null
  promoBottom: PublicPlacement | null
  className?: string
}

/** Right-side stacked promo banners (top + bottom). */
export function HomePromoStack({ promoTop, promoBottom, className }: HomePromoStackProps) {
  if (!promoTop && !promoBottom) {
    return null
  }

  return (
    <div className={cn('grid gap-4 sm:grid-cols-2 lg:grid-cols-1 lg:gap-4', className)}>
      {promoTop ? <PromoBannerCard placement={promoTop} tone="mint" priority /> : null}
      {promoBottom ? <PromoBannerCard placement={promoBottom} tone="cream" /> : null}
    </div>
  )
}
