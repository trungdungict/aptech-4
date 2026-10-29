import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/hooks/useAuth'
import * as cartApi from '@/features/cart/api/cart-api'
import { cartQueryKeys } from '@/features/cart/hooks/cart-query-keys'
import { CART_MAX_QUANTITY } from '@/features/cart/types'
import { ApiClientError } from '@/lib/api/api-error'
import { safeInternalPath } from '@/lib/safe-redirect'

import { getProduct } from '../api/catalog-api'
import { catalogQueryKeys } from '../hooks/catalog-query-keys'
import { formatProductPrice } from '../lib/format-price'

/** Matches Java `UUID.fromString` hex shape (backend path variable). */
const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function ProductDetailPage() {
  const { productId = '' } = useParams<{ productId: string }>()
  const idValid = UUID_RE.test(productId)
  const { status, user } = useAuth()
  const queryClient = useQueryClient()
  const [quantity, setQuantity] = useState(1)
  const [feedback, setFeedback] = useState<string | null>(null)

  const productQuery = useQuery({
    queryKey: catalogQueryKeys.productDetail(productId),
    queryFn: ({ signal }) => getProduct(productId, signal),
    enabled: idValid,
    retry: false,
  })

  const addMutation = useMutation({
    mutationFn: () => cartApi.addCartItem({ productId, quantity }),
    onSuccess: (cart) => {
      setFeedback('Added to cart.')
      if (user?.id) {
        queryClient.setQueryData(cartQueryKeys.detail(user.id), cart)
      }
    },
    onError: (error) => {
      if (error instanceof ApiClientError) {
        setFeedback(error.userMessage)
        return
      }
      setFeedback('Could not add to cart.')
    },
  })

  const inStock = productQuery.data?.inStock
  const canAdd =
    status === 'authenticated' &&
    productQuery.data != null &&
    productQuery.data.active &&
    inStock !== false &&
    !addMutation.isPending

  return (
    <section className="space-y-6">
      <p className="text-sm font-medium text-primary">
        <Link to="/" className="hover:underline">
          Home
        </Link>
        <span aria-hidden="true"> / </span>
        <Link to="/products" className="hover:underline">
          Catalog
        </Link>
        <span aria-hidden="true"> / </span>
        Detail
      </p>

      {!idValid ? (
        <UnavailableState
          title="Invalid product link"
          message="The product id in the URL is not a valid identifier."
        />
      ) : productQuery.isLoading ? (
        <p className="text-sm text-muted-foreground" role="status">
          Loading product…
        </p>
      ) : productQuery.isError ? (
        productQuery.error instanceof ApiClientError && productQuery.error.status === 404 ? (
          <UnavailableState
            title="Product unavailable"
            message="This product is missing, inactive, or belongs to an inactive category."
          />
        ) : (
          <div className="space-y-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            <p role="alert">
              {productQuery.error instanceof ApiClientError
                ? productQuery.error.userMessage
                : 'Unable to load this product.'}
            </p>
            <button
              type="button"
              className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground"
              onClick={() => void productQuery.refetch()}
            >
              Retry
            </button>
          </div>
        )
      ) : productQuery.data ? (
        <article className="grid gap-8 md:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]">
          <div className="flex aspect-[4/3] items-center justify-center rounded-2xl border border-border bg-muted text-muted-foreground">
            {productQuery.data.imageUrl ? (
              <img
                src={productQuery.data.imageUrl}
                alt=""
                className="h-full w-full rounded-2xl object-cover"
              />
            ) : (
              <span>No image available</span>
            )}
          </div>

          <div className="space-y-4">
            <p className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
              {productQuery.data.categoryName}
            </p>
            <h1 className="text-3xl font-semibold tracking-tight text-foreground">
              {productQuery.data.name}
            </h1>
            <p className="text-2xl font-semibold text-primary">
              {formatProductPrice(productQuery.data.unitPrice, productQuery.data.currency)}
            </p>
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
              <dt className="text-muted-foreground">SKU</dt>
              <dd className="font-medium text-foreground">{productQuery.data.sku}</dd>
              <dt className="text-muted-foreground">Availability</dt>
              <dd className="font-medium text-foreground">
                {productQuery.data.inStock === true
                  ? 'In stock'
                  : productQuery.data.inStock === false
                    ? 'Out of stock'
                    : 'Stock unknown'}
              </dd>
            </dl>
            {productQuery.data.description ? (
              <p className="text-sm leading-relaxed text-muted-foreground">
                {productQuery.data.description}
              </p>
            ) : (
              <p className="text-sm text-muted-foreground">No description provided.</p>
            )}

            {status === 'authenticated' ? (
              <div className="flex flex-wrap items-end gap-3">
                <label className="space-y-1 text-sm">
                  <span className="text-muted-foreground">Quantity</span>
                  <input
                    type="number"
                    min={1}
                    max={CART_MAX_QUANTITY}
                    value={quantity}
                    disabled={addMutation.isPending || inStock === false}
                    className="block w-20 rounded-lg border border-border bg-background px-2 py-1.5"
                    onChange={(event) => {
                      const next = Number(event.target.value)
                      if (Number.isInteger(next) && next >= 1 && next <= CART_MAX_QUANTITY) {
                        setQuantity(next)
                      }
                    }}
                  />
                </label>
                <button
                  type="button"
                  disabled={!canAdd}
                  className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-60"
                  onClick={() => {
                    setFeedback(null)
                    addMutation.mutate()
                  }}
                >
                  {addMutation.isPending ? 'Adding…' : 'Add to cart'}
                </button>
                <Link
                  to="/cart"
                  className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground"
                >
                  View cart
                </Link>
              </div>
            ) : status === 'booting' ? (
              <p className="text-sm text-muted-foreground">Checking session…</p>
            ) : (
              <p className="text-sm text-muted-foreground">
                <Link
                  className="font-medium text-primary underline-offset-4 hover:underline"
                  to={`/login?next=${encodeURIComponent(safeInternalPath(`/products/${productId}`))}`}
                >
                  Sign in
                </Link>{' '}
                to add this product to your cart.
              </p>
            )}

            {feedback ? (
              <p
                className={`text-sm ${addMutation.isError ? 'text-red-700' : 'text-emerald-800'}`}
                role="status"
              >
                {feedback}
              </p>
            ) : null}

            <p className="text-xs text-muted-foreground">
              Pricing and stock are server-authoritative. Adding to cart does not reserve inventory.
            </p>
            <Link
              to="/products"
              className="inline-flex rounded-lg border border-border bg-card px-4 py-2 text-sm font-medium text-foreground"
            >
              Back to catalog
            </Link>
          </div>
        </article>
      ) : null}
    </section>
  )
}

function UnavailableState({ title, message }: { title: string; message: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-border bg-card px-6 py-10 text-center">
      <h1 className="text-xl font-semibold text-foreground">{title}</h1>
      <p className="mt-2 text-sm text-muted-foreground">{message}</p>
      <Link
        to="/products"
        className="mt-4 inline-flex text-sm font-medium text-primary underline-offset-4 hover:underline"
      >
        Back to catalog
      </Link>
    </div>
  )
}
