import { ArrowRightIcon } from 'lucide-react'
import { Link } from 'react-router-dom'

import { cn } from '@/lib/utils'
import { useMediaQuery } from '@/hooks/use-media-query'

import { promotionCtaLabel, resolvePromotionCta } from '../lib/promotion-cta'
import { resolvePromotionImageUrl } from '../lib/resolve-promotion-image'
import type { PublicPlacement } from '../types'

type PromoBannerCardProps = {
  placement: PublicPlacement
  /** Visual tone for stacked side banners. */
  tone?: 'mint' | 'cream' | 'plain'
  className?: string
  priority?: boolean
}

/**
 * Single promo creative (HOME_PROMO_TOP / BOTTOM). Skips broken empty images.
 */
export function PromoBannerCard({
  placement,
  tone = 'plain',
  className,
  priority = false,
}: PromoBannerCardProps) {
  const preferMobile = useMediaQuery('(max-width: 767px)', false)
  const imageSrc = resolvePromotionImageUrl(
    placement.desktopImageUrl,
    placement.mobileImageUrl,
    preferMobile,
  )
  const nav = resolvePromotionCta(placement)
  const label = promotionCtaLabel(placement)
  const title = placement.title?.trim() || 'Promotion'
  const subtitle = placement.subtitle?.trim()

  const body = (
    <>
      <div className="flex flex-1 flex-col justify-center gap-1.5 p-4 sm:p-5">
        <p className="text-base font-semibold text-foreground sm:text-lg">{title}</p>
        {subtitle ? <p className="text-sm text-muted-foreground">{subtitle}</p> : null}
        {nav.kind !== 'none' ? (
          <span className="mt-1 inline-flex items-center gap-1 text-sm font-medium text-primary">
            {label}
            <ArrowRightIcon className="size-3.5" aria-hidden="true" />
          </span>
        ) : null}
      </div>
      {imageSrc ? (
        <div className="relative aspect-[16/10] w-full shrink-0 overflow-hidden bg-[#f4f7f5] sm:aspect-auto sm:h-full sm:w-40 md:w-44">
          <img
            src={imageSrc}
            alt=""
            className="h-full w-full object-cover object-center sm:object-contain sm:object-bottom sm:p-2"
            width={352}
            height={220}
            loading={priority ? 'eager' : 'lazy'}
            decoding="async"
            fetchPriority={priority ? 'high' : 'auto'}
          />
        </div>
      ) : null}
    </>
  )

  const shellClass = cn(
    'flex h-full min-h-[9.5rem] flex-col overflow-hidden rounded-xl border border-border/70 sm:flex-row',
    tone === 'mint' && 'bg-[#e7f4ee]',
    tone === 'cream' && 'bg-[#f7f1e8]',
    tone === 'plain' && 'bg-card',
    className,
  )

  if (nav.kind === 'internal') {
    return (
      <Link to={nav.to} className={cn(shellClass, 'transition hover:border-primary/35')}>
        {body}
      </Link>
    )
  }
  if (nav.kind === 'external') {
    return (
      <a href={nav.href} className={cn(shellClass, 'transition hover:border-primary/35')} rel="noopener noreferrer">
        {body}
      </a>
    )
  }
  return <div className={shellClass}>{body}</div>
}
