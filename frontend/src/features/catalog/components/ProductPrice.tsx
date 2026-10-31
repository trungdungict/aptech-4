import { formatProductPrice } from '../lib/format-price'

type ProductPriceProps = {
  unitPrice: string | number
  currency: string
  className?: string
}

export function ProductPrice({ unitPrice, currency, className }: ProductPriceProps) {
  return (
    <p className={className ?? 'text-lg font-semibold tracking-tight text-primary'}>
      {formatProductPrice(unitPrice, currency)}
    </p>
  )
}
