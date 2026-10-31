import { Badge } from '@/components/ui/badge'

type ProductStockBadgeProps = {
  inStock: boolean | null
}

export function ProductStockBadge({ inStock }: ProductStockBadgeProps) {
  if (inStock === true) {
    return <Badge variant="success">In stock</Badge>
  }
  if (inStock === false) {
    return <Badge variant="destructive">Out of stock</Badge>
  }
  return <Badge variant="secondary">Stock unknown</Badge>
}
