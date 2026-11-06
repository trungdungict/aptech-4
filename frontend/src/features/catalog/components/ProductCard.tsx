import { useMutation, useQueryClient } from '@tanstack/react-query'
import { MinusIcon, PlusIcon, ShoppingCartIcon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { useAuth } from '@/features/auth/hooks/useAuth'
import * as cartApi from '@/features/cart/api/cart-api'
import { cartQueryKeys } from '@/features/cart/hooks/cart-query-keys'
import { clampCartQuantity } from '@/features/cart/lib/clamp-quantity'
import { CART_MAX_QUANTITY } from '@/features/cart/types'
import { ApiClientError } from '@/lib/api/api-error'
import { safeInternalPath } from '@/lib/safe-redirect'

import type { Product } from '../types'
import { ProductImage } from './ProductImage'
import { ProductPrice } from './ProductPrice'
import { ProductStockBadge } from './ProductStockBadge'

type ProductCardProps = {
  product: Product
}

export function ProductCard({ product }: ProductCardProps) {
  const { t } = useTranslation('catalog')
  const { status, user } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [quantity, setQuantity] = useState(1)
  const [feedback, setFeedback] = useState<string | null>(null)

  const outOfStock = product.inStock === false || product.active === false
  const authenticated = status === 'authenticated'

  const addMutation = useMutation({
    mutationFn: () => cartApi.addCartItem({ productId: product.id, quantity }),
    onSuccess: (cart) => {
      setFeedback(t('card.added'))
      if (user?.id) {
        queryClient.setQueryData(cartQueryKeys.detail(user.id), cart)
      }
    },
    onError: (error) => {
      if (error instanceof ApiClientError) {
        setFeedback(error.userMessage)
        return
      }
      setFeedback(t('card.addError'))
    },
  })

  // Quantity is local UI state — guests may adjust before login redirect; stock still gates.
  const qtyDisabled = outOfStock || addMutation.isPending

  const handleAddToCart = () => {
    setFeedback(null)
    if (status === 'booting') {
      return
    }
    if (!authenticated) {
      const next = safeInternalPath(`${location.pathname}${location.search}`, '/products')
      void navigate(`/login?next=${encodeURIComponent(next)}`)
      return
    }
    if (outOfStock || addMutation.isPending) {
      return
    }
    addMutation.mutate()
  }

  const canIncrease = !qtyDisabled && quantity < CART_MAX_QUANTITY
  const canDecrease = !qtyDisabled && quantity > 1
  const addDisabled =
    status === 'booting' ||
    addMutation.isPending ||
    (authenticated && outOfStock)

  return (
    <Card className="h-full gap-0 overflow-hidden border-border/80 py-0 shadow-none transition hover:border-primary/50 hover:shadow-sm">
      <div className="flex h-full flex-col">
        <Link
          to={`/products/${product.id}`}
          className="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          aria-label={t('card.viewAria', { name: product.name })}
        >
          <ProductImage
            sku={product.sku}
            name={product.name}
            imageUrl={product.imageUrl}
            alt=""
            className="border-b border-border/60"
          />
        </Link>

        <CardContent className="flex flex-1 flex-col gap-3 p-4">
          <div className="space-y-2">
            <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
              {product.categoryName}
            </p>
            <h2 className="line-clamp-2 text-base font-semibold text-card-foreground">
              <Link
                to={`/products/${product.id}`}
                className="hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {product.name}
              </Link>
            </h2>
            <div className="flex flex-wrap items-end justify-between gap-2">
              <ProductPrice unitPrice={product.unitPrice} currency={product.currency} />
              <ProductStockBadge inStock={product.inStock} />
            </div>
          </div>

          <div className="mt-auto space-y-2 border-t border-border/60 pt-3">
            <div className="flex flex-wrap items-center gap-2">
              <div
                className="inline-flex items-center gap-1 rounded-md border border-border bg-background p-0.5"
                role="group"
                aria-label={t('card.quantityGroupAria', { name: product.name })}
              >
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t('card.decreaseAria', { name: product.name })}
                  disabled={!canDecrease || addMutation.isPending}
                  onClick={(event) => {
                    event.preventDefault()
                    event.stopPropagation()
                    setQuantity((current) => clampCartQuantity(current - 1))
                  }}
                >
                  <MinusIcon />
                </Button>
                <span
                  data-testid={`product-card-qty-${product.id}`}
                  className="min-w-8 text-center text-sm font-medium tabular-nums"
                  aria-live="polite"
                >
                  {quantity}
                </span>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-sm"
                  aria-label={t('card.increaseAria', { name: product.name })}
                  disabled={!canIncrease || addMutation.isPending}
                  onClick={(event) => {
                    event.preventDefault()
                    event.stopPropagation()
                    setQuantity((current) => clampCartQuantity(current + 1))
                  }}
                >
                  <PlusIcon />
                </Button>
              </div>

              <Button
                type="button"
                size="sm"
                className="min-w-0 flex-1"
                disabled={addDisabled}
                aria-label={t('card.addAria', { name: product.name })}
                onClick={(event) => {
                  event.preventDefault()
                  event.stopPropagation()
                  handleAddToCart()
                }}
              >
                <ShoppingCartIcon aria-hidden="true" />
                {addMutation.isPending ? t('card.adding') : t('card.addToCart')}
              </Button>
            </div>

            {feedback ? (
              <p
                className={`text-xs ${addMutation.isError ? 'text-destructive' : 'text-emerald-800'}`}
                role="status"
              >
                {feedback}
              </p>
            ) : null}
          </div>
        </CardContent>
      </div>
    </Card>
  )
}
