import { Link } from 'react-router-dom'

import { formatProductPrice } from '../lib/format-price'
import type { Product } from '../types'

type ProductCardProps = {
  product: Product
}

function stockLabel(inStock: boolean | null): string {
  if (inStock === true) return 'In stock'
  if (inStock === false) return 'Out of stock'
  return 'Stock unknown'
}

export function ProductCard({ product }: ProductCardProps) {
  return (
    <article className="flex h-full flex-col overflow-hidden rounded-2xl border border-border bg-card shadow-sm transition hover:border-primary/40">
      <Link
        to={`/products/${product.id}`}
        className="flex flex-1 flex-col focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        aria-label={`View ${product.name}`}
      >
        <div className="flex aspect-[4/3] items-center justify-center bg-muted text-sm text-muted-foreground">
          {product.imageUrl ? (
            <img
              src={product.imageUrl}
              alt=""
              className="h-full w-full object-cover"
              loading="lazy"
            />
          ) : (
            <span aria-hidden="true">No image</span>
          )}
        </div>
        <div className="flex flex-1 flex-col gap-2 p-4">
          <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
            {product.categoryName}
          </p>
          <h2 className="text-base font-semibold text-card-foreground">{product.name}</h2>
          <p className="mt-auto text-lg font-semibold text-primary">
            {formatProductPrice(product.unitPrice, product.currency)}
          </p>
          <p
            className={
              product.inStock === false
                ? 'text-xs font-medium text-red-600'
                : 'text-xs font-medium text-muted-foreground'
            }
          >
            {stockLabel(product.inStock)}
          </p>
        </div>
      </Link>
    </article>
  )
}
