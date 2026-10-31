import { ProductPrice } from '@/features/catalog/components/ProductPrice'
import { Separator } from '@/components/ui/separator'

import { hasOrderDiscount } from '../lib/order-money'
import type { AppliedPromotion } from '../types'

type OrderPriceSummaryProps = {
  currency: string
  subtotalAmount: string | number
  shippingAmount: string | number
  discountAmount?: string | number | null
  totalAmount: string | number
  appliedPromotions?: AppliedPromotion[] | null
}

/**
 * Displays backend-owned order money fields. Does not compute or round discounts.
 */
export function OrderPriceSummary({
  currency,
  subtotalAmount,
  shippingAmount,
  discountAmount = 0,
  totalAmount,
  appliedPromotions = [],
}: OrderPriceSummaryProps) {
  const showDiscount = hasOrderDiscount(discountAmount)
  const promotions = appliedPromotions ?? []

  return (
    <div className="space-y-3 text-sm">
      <div className="flex justify-between gap-3">
        <span className="text-muted-foreground">Subtotal</span>
        <ProductPrice
          unitPrice={subtotalAmount}
          currency={currency}
          className="text-sm font-medium text-foreground"
        />
      </div>
      <div className="flex justify-between gap-3">
        <span className="text-muted-foreground">Shipping</span>
        <ProductPrice
          unitPrice={shippingAmount}
          currency={currency}
          className="text-sm font-medium text-foreground"
        />
      </div>
      {showDiscount ? (
        <div className="space-y-2">
          <div className="flex justify-between gap-3">
            <span className="text-muted-foreground">Discount</span>
            <span className="text-sm font-medium text-emerald-700">
              −
              <ProductPrice
                unitPrice={discountAmount ?? 0}
                currency={currency}
                className="inline text-sm font-medium text-emerald-700"
              />
            </span>
          </div>
          {promotions.length > 0 ? (
            <ul className="space-y-1 rounded-lg bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
              {promotions.map((promo, index) => (
                <li
                  key={`${promo.campaignId ?? promo.campaignName}-${index}`}
                  className="flex justify-between gap-3"
                >
                  <span className="min-w-0 truncate">
                    {promo.campaignName}
                    {promo.strategyType ? (
                      <span className="opacity-70"> · {promo.strategyType}</span>
                    ) : null}
                  </span>
                  <ProductPrice
                    unitPrice={promo.discountAmount}
                    currency={currency}
                    className="shrink-0 text-xs font-medium text-foreground"
                  />
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
      <Separator />
      <div className="flex justify-between gap-3 font-semibold">
        <span>Total</span>
        <ProductPrice
          unitPrice={totalAmount}
          currency={currency}
          className="text-base font-semibold text-foreground"
        />
      </div>
    </div>
  )
}
