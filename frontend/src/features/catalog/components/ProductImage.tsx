import { ImageIcon } from 'lucide-react'

import { cn } from '@/lib/utils'

import { resolveProductImage } from '../lib/resolve-product-image'

type ProductImageProps = {
  sku?: string | null
  name?: string | null
  imageUrl?: string | null
  alt?: string
  className?: string
  imgClassName?: string
  /** Eager for LCP hero/product detail; lazy for grids. */
  priority?: boolean
  size?: 'card' | 'detail' | 'thumb'
}

export function ProductImage({
  sku,
  name,
  imageUrl,
  alt = '',
  className,
  imgClassName,
  priority = false,
  size = 'card',
}: ProductImageProps) {
  const src = resolveProductImage({ sku, name, imageUrl })
  const aspect =
    size === 'detail'
      ? 'aspect-[4/3] sm:aspect-square lg:aspect-[5/4]'
      : size === 'thumb'
        ? 'aspect-square'
        : 'aspect-[4/3]'

  return (
    <div
      className={cn(
        'relative flex items-center justify-center overflow-hidden bg-[#f4f7f5]',
        aspect,
        className,
      )}
    >
      {src ? (
        <img
          src={src}
          alt={alt}
          className={cn('h-full w-full object-contain p-3', imgClassName)}
          loading={priority ? 'eager' : 'lazy'}
          decoding={priority ? 'sync' : 'async'}
          fetchPriority={priority ? 'high' : 'auto'}
        />
      ) : (
        <div
          className="flex flex-col items-center gap-2 text-muted-foreground"
          aria-hidden={alt ? undefined : true}
        >
          <ImageIcon className={size === 'detail' ? 'size-12 opacity-50' : 'size-8 opacity-60'} />
          <span className="text-xs font-medium">No image</span>
        </div>
      )}
    </div>
  )
}
