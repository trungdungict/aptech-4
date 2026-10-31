import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MinusIcon, PlusIcon } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/hooks/useAuth'
import * as cartApi from '@/features/cart/api/cart-api'
import { cartQueryKeys } from '@/features/cart/hooks/cart-query-keys'
import { CART_MAX_QUANTITY } from '@/features/cart/types'
import { ApiClientError } from '@/lib/api/api-error'
import { safeInternalPath } from '@/lib/safe-redirect'

import { getProduct } from '../api/catalog-api'
import { ProductImage } from '../components/ProductImage'
import { ProductPrice } from '../components/ProductPrice'
import { ProductStockBadge } from '../components/ProductStockBadge'
import { catalogQueryKeys } from '../hooks/catalog-query-keys'
import type { Product } from '../types'

/** Matches Java `UUID.fromString` hex shape (backend path variable). */
const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function clampQuantity(value: number): number {
  if (!Number.isInteger(value)) {
    return 1
  }
  return Math.min(CART_MAX_QUANTITY, Math.max(1, value))
}

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

  const product = productQuery.data
  const inStock = product?.inStock
  const qtyDisabled = addMutation.isPending || inStock === false
  const canAdd =
    status === 'authenticated' &&
    product != null &&
    product.active &&
    inStock !== false &&
    !addMutation.isPending

  return (
    <section className="space-y-6">
      <nav aria-label="Breadcrumb" className="text-sm text-muted-foreground">
        <ol className="flex flex-wrap items-center gap-1.5">
          <li>
            <Link to="/" className="font-medium text-primary hover:underline">
              Home
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li>
            <Link to="/products" className="font-medium text-primary hover:underline">
              Catalog
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li className="max-w-[min(100%,20rem)] truncate font-medium text-foreground">
            {product?.name ?? 'Detail'}
          </li>
        </ol>
      </nav>

      {!idValid ? (
        <UnavailableState
          title="Invalid product link"
          message="The product id in the URL is not a valid identifier."
        />
      ) : productQuery.isLoading ? (
        <ProductDetailSkeleton />
      ) : productQuery.isError ? (
        productQuery.error instanceof ApiClientError && productQuery.error.status === 404 ? (
          <UnavailableState
            title="Product unavailable"
            message="This product is missing, inactive, or belongs to an inactive category."
          />
        ) : (
          <div className="space-y-3 rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
            <p role="alert">
              {productQuery.error instanceof ApiClientError
                ? productQuery.error.userMessage
                : 'Unable to load this product.'}
            </p>
            <Button type="button" size="sm" onClick={() => void productQuery.refetch()}>
              Retry
            </Button>
          </div>
        )
      ) : product ? (
        <ProductDetailContent
          product={product}
          productId={productId}
          status={status}
          quantity={quantity}
          setQuantity={setQuantity}
          qtyDisabled={qtyDisabled}
          canAdd={canAdd}
          addPending={addMutation.isPending}
          addError={addMutation.isError}
          feedback={feedback}
          onAdd={() => {
            setFeedback(null)
            addMutation.mutate()
          }}
        />
      ) : null}
    </section>
  )
}

type AuthStatus = ReturnType<typeof useAuth>['status']

type ProductDetailContentProps = {
  product: Product
  productId: string
  status: AuthStatus
  quantity: number
  setQuantity: (value: number) => void
  qtyDisabled: boolean
  canAdd: boolean
  addPending: boolean
  addError: boolean
  feedback: string | null
  onAdd: () => void
}

function ProductDetailContent({
  product,
  productId,
  status,
  quantity,
  setQuantity,
  qtyDisabled,
  canAdd,
  addPending,
  addError,
  feedback,
  onAdd,
}: ProductDetailContentProps) {
  return (
    <article className="grid gap-8 lg:grid-cols-[minmax(0,1.05fr)_minmax(0,0.95fr)] lg:items-start">
      <div className="overflow-hidden rounded-2xl border border-border bg-card shadow-sm">
        <ProductImage
          sku={product.sku}
          name={product.name}
          imageUrl={product.imageUrl}
          alt={product.name}
          priority
          size="detail"
          imgClassName="p-6 sm:p-8"
        />
      </div>

      <div className="space-y-5">
        <div className="space-y-3">
          {product.categoryName ? (
            <p className="text-xs font-semibold tracking-wide text-primary uppercase">
              {product.categoryName}
            </p>
          ) : null}
          <h1 className="text-3xl font-semibold tracking-tight text-foreground md:text-4xl">
            {product.name}
          </h1>
          <div className="flex flex-wrap items-center gap-3">
            <ProductPrice
              unitPrice={product.unitPrice}
              currency={product.currency}
              className="text-2xl font-semibold tracking-tight text-primary"
            />
            <ProductStockBadge inStock={product.inStock} />
          </div>
        </div>

        <Separator />

        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          {product.sku ? (
            <>
              <dt className="text-muted-foreground">SKU</dt>
              <dd className="font-medium text-foreground">{product.sku}</dd>
            </>
          ) : null}
          {product.categoryName ? (
            <>
              <dt className="text-muted-foreground">Category</dt>
              <dd className="font-medium text-foreground">{product.categoryName}</dd>
            </>
          ) : null}
        </dl>

        {product.description ? (
          <p className="text-sm leading-relaxed text-muted-foreground whitespace-pre-wrap">
            {product.description}
          </p>
        ) : (
          <p className="text-sm text-muted-foreground">No description provided.</p>
        )}

        <Separator />

        {status === 'authenticated' ? (
          <div className="space-y-3">
            <div className="flex flex-wrap items-end gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="product-quantity">Quantity</Label>
                <div className="flex items-center gap-1">
                  <Button
                    type="button"
                    variant="outline"
                    size="icon-sm"
                    aria-label="Decrease quantity"
                    disabled={qtyDisabled || quantity <= 1}
                    onClick={() => setQuantity(clampQuantity(quantity - 1))}
                  >
                    <MinusIcon />
                  </Button>
                  <Input
                    id="product-quantity"
                    type="number"
                    min={1}
                    max={CART_MAX_QUANTITY}
                    value={quantity}
                    disabled={qtyDisabled}
                    className="w-16 text-center tabular-nums"
                    aria-valuemin={1}
                    aria-valuemax={CART_MAX_QUANTITY}
                    onChange={(event) => {
                      const next = Number(event.target.value)
                      if (Number.isInteger(next) && next >= 1 && next <= CART_MAX_QUANTITY) {
                        setQuantity(next)
                      }
                    }}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="icon-sm"
                    aria-label="Increase quantity"
                    disabled={qtyDisabled || quantity >= CART_MAX_QUANTITY}
                    onClick={() => setQuantity(clampQuantity(quantity + 1))}
                  >
                    <PlusIcon />
                  </Button>
                </div>
              </div>

              <Button type="button" size="lg" disabled={!canAdd} onClick={onAdd}>
                {addPending ? 'Adding…' : 'Add to cart'}
              </Button>

              <Button asChild variant="outline" size="lg">
                <Link to="/cart">View cart</Link>
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              Max {CART_MAX_QUANTITY} per line. Pricing and stock are server-authoritative; adding
              to cart does not reserve inventory.
            </p>
          </div>
        ) : status === 'booting' ? (
          <p className="text-sm text-muted-foreground" role="status">
            Checking session…
          </p>
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
            className={`text-sm ${addError ? 'text-destructive' : 'text-emerald-800'}`}
            role="status"
          >
            {feedback}
          </p>
        ) : null}

        <Button asChild variant="outline">
          <Link to="/products">Back to catalog</Link>
        </Button>
      </div>
    </article>
  )
}

function ProductDetailSkeleton() {
  return (
    <div className="grid gap-8 lg:grid-cols-2" role="status" aria-live="polite">
      <p className="sr-only">Loading product…</p>
      <Skeleton className="aspect-[4/3] w-full rounded-2xl" />
      <div className="space-y-4">
        <Skeleton className="h-4 w-24" />
        <Skeleton className="h-10 w-[75%] max-w-md" />
        <Skeleton className="h-8 w-32" />
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-10 w-48" />
      </div>
    </div>
  )
}

function UnavailableState({ title, message }: { title: string; message: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-border bg-card px-6 py-10 text-center">
      <h1 className="text-xl font-semibold text-foreground">{title}</h1>
      <p className="mt-2 text-sm text-muted-foreground">{message}</p>
      <Button asChild variant="link" className="mt-4">
        <Link to="/products">Back to catalog</Link>
      </Button>
    </div>
  )
}
