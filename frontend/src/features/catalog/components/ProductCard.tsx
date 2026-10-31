import { Link } from 'react-router-dom'

import { Card, CardContent } from '@/components/ui/card'

import type { Product } from '../types'
import { ProductImage } from './ProductImage'
import { ProductPrice } from './ProductPrice'
import { ProductStockBadge } from './ProductStockBadge'

type ProductCardProps = {
  product: Product
}

export function ProductCard({ product }: ProductCardProps) {
  return (
    <Card className="h-full gap-0 overflow-hidden border-border/80 py-0 shadow-none transition hover:border-primary/50 hover:shadow-sm">
      <Link
        to={`/products/${product.id}`}
        className="flex h-full flex-col focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
        aria-label={`View ${product.name}`}
      >
        <ProductImage
          sku={product.sku}
          name={product.name}
          imageUrl={product.imageUrl}
          alt=""
          className="border-b border-border/60"
        />
        <CardContent className="flex flex-1 flex-col gap-2 p-4">
          <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
            {product.categoryName}
          </p>
          <h2 className="line-clamp-2 text-base font-semibold text-card-foreground">
            {product.name}
          </h2>
          <div className="mt-auto flex flex-wrap items-end justify-between gap-2 pt-2">
            <ProductPrice unitPrice={product.unitPrice} currency={product.currency} />
            <ProductStockBadge inStock={product.inStock} />
          </div>
        </CardContent>
      </Link>
    </Card>
  )
}
