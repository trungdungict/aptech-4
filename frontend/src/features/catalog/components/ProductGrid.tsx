import type { Product } from '../types'
import { ProductCard } from './ProductCard'
import { Skeleton } from '@/components/ui/skeleton'

type ProductGridProps = {
  products: Product[]
}

export function ProductGrid({ products }: ProductGridProps) {
  return (
    <ul className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-3 xl:grid-cols-4">
      {products.map((product) => (
        <li key={product.id} className="h-full">
          <ProductCard product={product} />
        </li>
      ))}
    </ul>
  )
}

export function ProductGridSkeleton({ count = 6 }: { count?: number }) {
  return (
    <ul
      className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-3 xl:grid-cols-4"
      aria-hidden="true"
    >
      {Array.from({ length: count }, (_, index) => (
        <li key={index}>
          <div className="overflow-hidden rounded-xl border border-border bg-card">
            <Skeleton className="aspect-[4/3] w-full rounded-none" />
            <div className="space-y-3 p-4">
              <Skeleton className="h-3 w-20" />
              <Skeleton className="h-5 w-[75%]" />
              <Skeleton className="h-5 w-24" />
            </div>
          </div>
        </li>
      ))}
    </ul>
  )
}
