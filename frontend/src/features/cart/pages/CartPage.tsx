import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'

import { useAuth } from '@/features/auth/hooks/useAuth'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { ApiClientError } from '@/lib/api/api-error'

import * as cartApi from '../api/cart-api'
import { cartQueryKeys } from '../hooks/cart-query-keys'
import { CART_MAX_QUANTITY, type Cart } from '../types'

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

  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight text-foreground">Cart</h1>
        <p className="text-sm text-muted-foreground">
          Quantities and prices come from the server. Stock is rechecked at checkout.
        </p>
      </div>

      {cartQuery.isLoading ? (
        <p className="text-sm text-muted-foreground" role="status">
          Loading cart…
        </p>
      ) : cartQuery.isError ? (
        <div className="space-y-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          <p role="alert">
            {cartQuery.error instanceof ApiClientError
              ? cartQuery.error.status === 403
                ? 'Cart is available for customer accounts only.'
                : cartQuery.error.userMessage
              : 'Unable to load cart.'}
          </p>
          <button
            type="button"
            className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground"
            onClick={() => void cartQuery.refetch()}
          >
            Retry
          </button>
        </div>
      ) : isEmpty ? (
        <div className="rounded-2xl border border-dashed border-border bg-card px-6 py-10 text-center">
          <p className="text-sm text-muted-foreground">Your cart is empty.</p>
          <Link
            to="/products"
            className="mt-4 inline-flex rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
          >
            Browse catalog
          </Link>
        </div>
      ) : cart ? (
        <>
          {cart.hasUnavailableItems ? (
            <p className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900" role="status">
              Some items are no longer available. Remove them before checkout.
            </p>
          ) : null}

          {mutationError ? (
            <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
              {mutationError instanceof ApiClientError
                ? mutationError.userMessage
                : 'Cart update failed.'}
            </p>
          ) : null}

          <ul className="divide-y divide-border rounded-2xl border border-border bg-card">
            {cart.items.map((item) => {
              const busy = pendingItemId === item.id || clearMutation.isPending
              return (
                <li key={item.id} className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center">
                  <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-lg border border-border bg-muted text-xs text-muted-foreground">
                    {item.imageUrl ? (
                      <img src={item.imageUrl} alt="" className="h-full w-full rounded-lg object-cover" />
                    ) : (
                      'No image'
                    )}
                  </div>
                  <div className="min-w-0 flex-1 space-y-1">
                    <Link
                      to={`/products/${item.productId}`}
                      className="font-medium text-foreground hover:underline"
                    >
                      {item.productName}
                    </Link>
                    <p className="text-sm text-muted-foreground">
                      {formatProductPrice(item.unitPrice, item.currency)} each
                    </p>
                    {!item.available ? (
                      <p className="text-sm font-medium text-amber-800">Unavailable</p>
                    ) : null}
                  </div>
                  <div className="flex flex-wrap items-center gap-3">
                    <label className="flex items-center gap-2 text-sm">
                      <span className="text-muted-foreground">Qty</span>
                      <input
                        type="number"
                        min={1}
                        max={CART_MAX_QUANTITY}
                        value={item.quantity}
                        disabled={busy || !item.available}
                        aria-label={`Quantity for ${item.productName}`}
                        className="w-16 rounded-lg border border-border bg-background px-2 py-1.5"
                        onChange={(event) => {
                          const next = Number(event.target.value)
                          if (!Number.isInteger(next) || next < 1 || next > CART_MAX_QUANTITY) {
                            return
                          }
                          updateMutation.reset()
                          removeMutation.reset()
                          updateMutation.mutate({ itemId: item.id, quantity: next })
                        }}
                      />
                    </label>
                    <p className="min-w-[6rem] text-sm font-semibold text-foreground">
                      {formatProductPrice(item.lineSubtotal, item.currency)}
                    </p>
                    <button
                      type="button"
                      disabled={busy}
                      className="rounded-lg border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-muted disabled:opacity-60"
                      onClick={() => {
                        updateMutation.reset()
                        removeMutation.reset()
                        removeMutation.mutate(item.id)
                      }}
                    >
                      Remove
                    </button>
                  </div>
                </li>
              )
            })}
          </ul>

          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-lg font-semibold text-foreground">
              Subtotal {formatProductPrice(cart.subtotal, cart.currency)}
            </p>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                disabled={clearMutation.isPending || Boolean(pendingItemId)}
                className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-muted disabled:opacity-60"
                onClick={() => {
                  clearMutation.reset()
                  clearMutation.mutate()
                }}
              >
                {clearMutation.isPending ? 'Clearing…' : 'Clear cart'}
              </button>
              <Link
                to="/products"
                className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-muted"
              >
                Continue shopping
              </Link>
              {canCheckout ? (
                <Link
                  to="/checkout"
                  className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
                >
                  Checkout
                </Link>
              ) : (
                <span className="rounded-lg bg-muted px-4 py-2 text-sm text-muted-foreground">
                  Checkout unavailable
                </span>
              )}
            </div>
          </div>
        </>
      ) : null}
    </section>
  )
}
