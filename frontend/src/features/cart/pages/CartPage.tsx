import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ImageIcon, MinusIcon, PlusIcon, ShoppingBagIcon } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { ProductPrice } from '@/features/catalog/components/ProductPrice'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { ApiClientError } from '@/lib/api/api-error'

import * as cartApi from '../api/cart-api'
import { cartQueryKeys } from '../hooks/cart-query-keys'
import { CART_MAX_QUANTITY, type Cart, type CartItem } from '../types'

export function CartPage() {
  const { user } = useAuth()
  const userId = user?.id ?? ''
  const queryClient = useQueryClient()

  const cartQuery = useQuery({
    queryKey: cartQueryKeys.detail(userId),
    queryFn: ({ signal }) => cartApi.getCart(signal),
    enabled: Boolean(userId),
    retry: false,
  })

  const setCartCache = (cart: Cart) => {
    queryClient.setQueryData(cartQueryKeys.detail(userId), cart)
  }

  const updateMutation = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: string; quantity: number }) =>
      cartApi.updateCartItem(itemId, { quantity }),
    onSuccess: setCartCache,
  })

  const removeMutation = useMutation({
    mutationFn: (itemId: string) => cartApi.removeCartItem(itemId),
    onSuccess: setCartCache,
  })

  const clearMutation = useMutation({
    mutationFn: () => cartApi.clearCart(),
    onSuccess: setCartCache,
  })

  const pendingItemId =
    updateMutation.isPending && updateMutation.variables
      ? updateMutation.variables.itemId
      : removeMutation.isPending && removeMutation.variables
        ? removeMutation.variables
        : null

  const mutationError =
    updateMutation.error ?? removeMutation.error ?? clearMutation.error

  const cart = cartQuery.data
  const isEmpty = cart != null && cart.items.length === 0
  const canCheckout =
    cart != null && cart.items.length > 0 && !cart.hasUnavailableItems

  const requestQuantity = (itemId: string, quantity: number) => {
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > CART_MAX_QUANTITY) {
      return
    }
    updateMutation.reset()
    removeMutation.reset()
    updateMutation.mutate({ itemId, quantity })
  }

  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight text-foreground">Cart</h1>
        <p className="text-sm text-muted-foreground">
          Quantities and prices come from the server. Stock is rechecked at checkout.
        </p>
      </div>

      {cartQuery.isLoading ? (
        <CartLoadingSkeleton />
      ) : cartQuery.isError ? (
        <div className="space-y-3 rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          <p role="alert">
            {cartQuery.error instanceof ApiClientError
              ? cartQuery.error.status === 403
                ? 'Cart is available for customer accounts only.'
                : cartQuery.error.userMessage
              : 'Unable to load cart.'}
          </p>
          <Button type="button" size="sm" onClick={() => void cartQuery.refetch()}>
            Retry
          </Button>
        </div>
      ) : isEmpty ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <ShoppingBagIcon className="size-10 text-muted-foreground opacity-60" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">Your cart is empty.</p>
            <Button asChild>
              <Link to="/products">Browse catalog</Link>
            </Button>
          </CardContent>
        </Card>
      ) : cart ? (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,0.8fr)] lg:items-start">
          <div className="space-y-4">
            {cart.hasUnavailableItems ? (
              <p
                className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
                role="status"
              >
                Some items are no longer available. Remove them before checkout.
              </p>
            ) : null}

            {mutationError ? (
              <p
                className="rounded-xl border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive"
                role="alert"
              >
                {mutationError instanceof ApiClientError
                  ? mutationError.userMessage
                  : 'Cart update failed.'}
              </p>
            ) : null}

            <ul className="space-y-3">
              {cart.items.map((item) => {
                const busy = pendingItemId === item.id || clearMutation.isPending
                return (
                  <CartLineItem
                    key={item.id}
                    item={item}
                    busy={busy}
                    onQuantityChange={(quantity) => requestQuantity(item.id, quantity)}
                    onRemove={() => {
                      updateMutation.reset()
                      removeMutation.reset()
                      removeMutation.mutate(item.id)
                    }}
                  />
                )
              })}
            </ul>
          </div>

          <Card className="sticky top-20 shadow-sm">
            <CardHeader>
              <CardTitle>Order summary</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div className="flex items-center justify-between gap-3">
                <span className="text-muted-foreground">Subtotal</span>
                <ProductPrice
                  unitPrice={cart.subtotal}
                  currency={cart.currency}
                  className="text-base font-semibold text-foreground"
                />
              </div>
              <p className="text-xs text-muted-foreground">
                Shipping and final totals are confirmed by the server at checkout. No tax or
                discount lines are applied in this demo.
              </p>
            </CardContent>
            <Separator />
            <CardFooter className="flex flex-col gap-2 pt-4 sm:items-stretch">
              {canCheckout ? (
                <Button asChild size="lg" className="w-full">
                  <Link to="/checkout">Checkout</Link>
                </Button>
              ) : (
                <Button type="button" size="lg" className="w-full" disabled>
                  Checkout unavailable
                </Button>
              )}
              <Button asChild variant="outline" className="w-full">
                <Link to="/products">Continue shopping</Link>
              </Button>
              <Button
                type="button"
                variant="ghost"
                className="w-full"
                disabled={clearMutation.isPending || Boolean(pendingItemId)}
                onClick={() => {
                  clearMutation.reset()
                  clearMutation.mutate()
                }}
              >
                {clearMutation.isPending ? 'Clearing…' : 'Clear cart'}
              </Button>
            </CardFooter>
          </Card>
        </div>
      ) : null}
    </section>
  )
}

type CartLineItemProps = {
  item: CartItem
  busy: boolean
  onQuantityChange: (quantity: number) => void
  onRemove: () => void
}

function CartLineItem({ item, busy, onQuantityChange, onRemove }: CartLineItemProps) {
  const qtyDisabled = busy || !item.available

  return (
    <li>
      <Card className="gap-0 overflow-hidden py-0 shadow-sm">
        <CardContent className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center">
          <Link
            to={`/products/${item.productId}`}
            className="flex size-20 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-border bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={`View ${item.productName}`}
          >
            {item.imageUrl ? (
              <img
                src={item.imageUrl}
                alt=""
                className="h-full w-full object-cover"
              />
            ) : (
              <span className="flex flex-col items-center gap-1 text-muted-foreground" aria-hidden="true">
                <ImageIcon className="size-5 opacity-60" />
                <span className="text-[10px] font-medium">No image</span>
              </span>
            )}
          </Link>

          <div className="min-w-0 flex-1 space-y-2">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="space-y-1">
                <Link
                  to={`/products/${item.productId}`}
                  className="font-medium text-foreground hover:underline"
                >
                  {item.productName}
                </Link>
                <p className="text-sm text-muted-foreground">
                  {formatProductPrice(item.unitPrice, item.currency)} each
                </p>
              </div>
              {!item.available ? <Badge variant="warning">Unavailable</Badge> : null}
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <div className="flex items-center gap-1">
                <Button
                  type="button"
                  variant="outline"
                  size="icon-sm"
                  aria-label={`Decrease ${item.productName} quantity`}
                  disabled={qtyDisabled || item.quantity <= 1}
                  onClick={() => onQuantityChange(item.quantity - 1)}
                >
                  <MinusIcon />
                </Button>
                <Input
                  type="number"
                  min={1}
                  max={CART_MAX_QUANTITY}
                  value={item.quantity}
                  disabled={qtyDisabled}
                  aria-label={`Quantity for ${item.productName}`}
                  className="w-16 text-center tabular-nums"
                  onChange={(event) => {
                    onQuantityChange(Number(event.target.value))
                  }}
                />
                <Button
                  type="button"
                  variant="outline"
                  size="icon-sm"
                  aria-label={`Increase ${item.productName} quantity`}
                  disabled={qtyDisabled || item.quantity >= CART_MAX_QUANTITY}
                  onClick={() => onQuantityChange(item.quantity + 1)}
                >
                  <PlusIcon />
                </Button>
              </div>

              <ProductPrice
                unitPrice={item.lineSubtotal}
                currency={item.currency}
                className="min-w-[6rem] text-sm font-semibold text-foreground"
              />

              <Button type="button" variant="outline" size="sm" disabled={busy} onClick={onRemove}>
                Remove
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </li>
  )
}

function CartLoadingSkeleton() {
  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,0.8fr)]" role="status" aria-live="polite">
      <p className="sr-only">Loading cart…</p>
      <div className="space-y-3">
        {Array.from({ length: 2 }, (_, i) => (
          <Skeleton key={i} className="h-28 w-full rounded-xl" />
        ))}
      </div>
      <Skeleton className="h-56 w-full rounded-xl" />
    </div>
  )
}
