import { useAppCurrency } from '@/features/i18n/hooks/useAppCurrency'

import { formatProductPrice } from '../lib/format-price'

type ProductPriceProps = {
  unitPrice: string | number
  currency: string
  className?: string
}

export function ProductPrice({ unitPrice, currency, className }: ProductPriceProps) {
  // Subscribe so prices re-render when the currency preference changes.
  const { currency: displayCurrency } = useAppCurrency()

  return (
    <span className={className} data-display-currency={displayCurrency}>
      {formatProductPrice(unitPrice, currency, undefined, displayCurrency)}
    </span>
  )
}
