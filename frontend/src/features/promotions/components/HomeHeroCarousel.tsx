import { ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { useMediaQuery } from '@/hooks/use-media-query'
import { cn } from '@/lib/utils'

import { promotionCtaLabel, resolvePromotionCta } from '../lib/promotion-cta'
import { resolvePromotionImageUrl } from '../lib/resolve-promotion-image'
import type { PublicPlacement } from '../types'

type HomeHeroCarouselProps = {
  slides: PublicPlacement[]
  className?: string
}

/**
 * Large left-side hero carousel. Prefers slides with a resolvable image URL;
 * falls back to title-only gradient when the image fails to load.
 */
export function HomeHeroCarousel({ slides, className }: HomeHeroCarouselProps) {
  const { t } = useTranslation('promotions')
  const preferMobile = useMediaQuery('(max-width: 767px)', false)
  const usable = slides.filter((slide) =>
    Boolean(resolvePromotionImageUrl(slide.desktopImageUrl, slide.mobileImageUrl, preferMobile)),
  )
  const [index, setIndex] = useState(0)
  const [imageFailed, setImageFailed] = useState(false)

  const safeIndex = usable.length === 0 ? 0 : Math.min(index, usable.length - 1)
  const active = usable[safeIndex]
  const imageSrc = active
    ? resolvePromotionImageUrl(active.desktopImageUrl, active.mobileImageUrl, preferMobile)
    : null

  useEffect(() => {
    setIndex(0)
  }, [usable.length, usable[0]?.id])

  useEffect(() => {
    setImageFailed(false)
  }, [imageSrc, active?.id])

  const go = useCallback(
    (delta: number) => {
      if (usable.length === 0) {
        return
      }
      setIndex((current) => (current + delta + usable.length) % usable.length)
    },
    [usable.length],
  )

  useEffect(() => {
    if (usable.length < 2 || typeof window === 'undefined') {
      return
    }
    if (
      typeof window.matchMedia === 'function'
      && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    ) {
      return
    }
    const timer = window.setInterval(() => go(1), 7000)
    return () => window.clearInterval(timer)
  }, [go, usable.length])

  if (usable.length === 0 || !active || !imageSrc) {
    return (
      <div
        className={cn(
          'flex min-h-[16rem] items-center justify-center rounded-xl border border-border/70 bg-[#f4f7f5] text-sm text-muted-foreground lg:min-h-[22rem]',
          className,
        )}
        role="status"
      >
        {t('carousel.empty')}
      </div>
    )
  }

  const nav = resolvePromotionCta(active)
  const label = promotionCtaLabel(active, t('cta.shopNow'))
  const title = active.title?.trim() || t('carousel.featured')
  const subtitle = active.subtitle?.trim()

  const media = (
    <div className="relative aspect-[16/10] w-full overflow-hidden bg-[#e8f2ee] lg:aspect-auto lg:h-full lg:min-h-[22rem]">
      {!imageFailed ? (
        <img
          src={imageSrc}
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          width={960}
          height={600}
          fetchPriority="high"
          decoding="async"
          onError={() => setImageFailed(true)}
        />
      ) : null}
      <div
        className={cn(
          'absolute inset-0 bg-gradient-to-t from-black/55 via-black/15 to-transparent',
          imageFailed && 'from-black/70 via-[#1a3d32]/45 to-[#e8f2ee]',
        )}
        aria-hidden="true"
      />
      <div className="absolute inset-x-0 bottom-0 space-y-2 p-5 sm:p-6">
        <p className="max-w-lg text-xl font-semibold text-white drop-shadow sm:text-2xl">{title}</p>
        {subtitle ? (
          <p className="max-w-md text-sm text-white/90 drop-shadow sm:text-base">{subtitle}</p>
        ) : null}
        {nav.kind !== 'none' ? (
          <span className="inline-flex items-center rounded-md bg-white/95 px-3 py-1.5 text-sm font-medium text-foreground">
            {label}
          </span>
        ) : null}
      </div>
    </div>
  )

  return (
    <div
      className={cn('relative overflow-hidden rounded-xl border border-border/70', className)}
      aria-roledescription="carousel"
      aria-label={t('carousel.ariaLabel')}
    >
      {nav.kind === 'internal' ? (
        <Link to={nav.to} className="block focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
          {media}
        </Link>
      ) : nav.kind === 'external' ? (
        <a href={nav.href} className="block" rel="noopener noreferrer">
          {media}
        </a>
      ) : (
        media
      )}

      {usable.length > 1 ? (
        <>
          <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center p-2">
            <Button
              type="button"
              size="icon-sm"
              variant="secondary"
              className="pointer-events-auto bg-white/90 shadow"
              aria-label={t('carousel.previous')}
              onClick={() => go(-1)}
            >
              <ChevronLeftIcon className="size-4" />
            </Button>
          </div>
          <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center p-2">
            <Button
              type="button"
              size="icon-sm"
              variant="secondary"
              className="pointer-events-auto bg-white/90 shadow"
              aria-label={t('carousel.next')}
              onClick={() => go(1)}
            >
              <ChevronRightIcon className="size-4" />
            </Button>
          </div>
          <div className="absolute bottom-3 left-1/2 flex -translate-x-1/2 gap-1.5" role="tablist" aria-label={t('carousel.slidesAria')}>
            {usable.map((slide, i) => (
              <button
                key={slide.id}
                type="button"
                role="tab"
                aria-selected={i === safeIndex}
                aria-label={t('carousel.slideAria', { n: i + 1 })}
                className={cn(
                  'size-2 rounded-full transition',
                  i === safeIndex ? 'bg-white' : 'bg-white/50 hover:bg-white/80',
                )}
                onClick={() => setIndex(i)}
              />
            ))}
          </div>
        </>
      ) : null}
    </div>
  )
}
