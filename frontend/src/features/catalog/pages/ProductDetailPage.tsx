import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MinusIcon, PlusIcon } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/hooks/useAuth'
import * as cartApi from '@/features/cart/api/cart-api'
import { cartQueryKeys } from '@/features/cart/hooks/cart-query-keys'
import { clampCartQuantity } from '@/features/cart/lib/clamp-quantity'
import { CART_MAX_QUANTITY } from '@/features/cart/types'
import { useAppLocale } from '@/features/i18n/hooks/useAppLocale'
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

export function ProductDetailPage() {
  const { t } = useTranslation('catalog')
  const { t: tc } = useTranslation('common')
  const { productId = '' } = useParams<{ productId: string }>()
  const idValid = UUID_RE.test(productId)
  const { status, user } = useAuth()
  const { locale } = useAppLocale()
  const queryClient = useQueryClient()
  const [quantity, setQuantity] = useState(1)
  const [feedback, setFeedback] = useState<string | null>(null)

  const productQuery = useQuery({
    queryKey: catalogQueryKeys.productDetail(locale, productId),
    queryFn: ({ signal }) => getProduct(productId, signal),
    enabled: idValid,
    retry: false,
  })

  const addMutation = useMutation({
    mutationFn: () => cartApi.addCartItem({ productId, quantity }),
    onSuccess: (cart) => {
      setFeedback(t('detail.added'))
      if (user?.id) {
        queryClient.setQueryData(cartQueryKeys.detail(user.id), cart)
      }
    },
    onError: (error) => {
      if (error instanceof ApiClientError) {
        setFeedback(error.userMessage)
        return
      }
      setFeedback(t('detail.addError'))
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
      <nav aria-label={t('detail.breadcrumbAria')} className="text-sm text-muted-foreground">
        <ol className="flex flex-wrap items-center gap-1.5">
          <li>
            <Link to="/" className="font-medium text-primary hover:underline">
              {tc('nav.home')}
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li>
            <Link to="/products" className="font-medium text-primary hover:underline">
              {tc('nav.catalog')}
            </Link>
          </li>
          <li aria-hidden="true">/</li>
          <li className="max-w-[min(100%,20rem)] truncate font-medium text-foreground">
            {product?.name ?? tc('nav.detail')}
          </li>
        </ol>
      </nav>

      {!idValid ? (
        <UnavailableState
          title={t('detail.invalidLink')}
          message={t('detail.invalidLinkMessage')}
        />
      ) : productQuery.isLoading ? (
        <ProductDetailSkeleton />
      ) : productQuery.isError ? (
        productQuery.error instanceof ApiClientError && productQuery.error.status === 404 ? (
          <UnavailableState
            title={t('detail.unavailable')}
            message={t('detail.unavailableMessage')}
          />
        ) : (
          <div className="space-y-3 rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
            <p role="alert">
              {productQuery.error instanceof ApiClientError
                ? productQuery.error.userMessage
                : t('detail.loadError')}
            </p>
            <Button type="button" size="sm" onClick={() => void productQuery.refetch()}>
              {tc('actions.retry')}
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
  const { t } = useTranslation('catalog')
  const { t: tc } = useTranslation('common')
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
              <dt className="text-muted-foreground">{t('detail.sku')}</dt>
              <dd className="font-medium text-foreground">{product.sku}</dd>
            </>
          ) : null}
          {product.categoryName ? (
            <>
              <dt className="text-muted-foreground">{t('detail.category')}</dt>
              <dd className="font-medium text-foreground">{product.categoryName}</dd>
            </>
          ) : null}
        </dl>

        {product.description ? (
          <p className="text-sm leading-relaxed text-muted-foreground whitespace-pre-wrap">
            {product.description}
          </p>
        ) : (
          <p className="text-sm text-muted-foreground">{t('detail.noDescription')}</p>
        )}

        <Separator />

        {status === 'authenticated' ? (
          <div className="space-y-3">
            <div className="flex flex-wrap items-end gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="product-quantity">{t('detail.quantity')}</Label>
                <div className="flex items-center gap-1">
                  <Button
                    type="button"
                    variant="outline"
                    size="icon-sm"
                    aria-label={t('detail.decreaseQty')}
                    disabled={qtyDisabled || quantity <= 1}
                    onClick={() => setQuantity(clampCartQuantity(quantity - 1))}
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
                    aria-label={t('detail.increaseQty')}
                    disabled={qtyDisabled || quantity >= CART_MAX_QUANTITY}
                    onClick={() => setQuantity(clampCartQuantity(quantity + 1))}
                  >
                    <PlusIcon />
                  </Button>
                </div>
              </div>

              <Button type="button" size="lg" disabled={!canAdd} onClick={onAdd}>
                {addPending ? t('detail.adding') : t('detail.addToCart')}
              </Button>

              <Button asChild variant="outline" size="lg">
                <Link to="/cart">{t('detail.viewCart')}</Link>
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              {t('detail.maxPerLine', { max: CART_MAX_QUANTITY })}
            </p>
          </div>
        ) : status === 'booting' ? (
          <p className="text-sm text-muted-foreground" role="status">
            {tc('status.checkingSession')}
          </p>
        ) : (
          <p className="text-sm text-muted-foreground">
            <Link
              className="font-medium text-primary underline-offset-4 hover:underline"
              to={`/login?next=${encodeURIComponent(safeInternalPath(`/products/${productId}`))}`}
            >
              {t('detail.signInLink')}
            </Link>{' '}
            {t('detail.signInSuffix')}
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
          <Link to="/products">{t('detail.backToCatalog')}</Link>
        </Button>
      </div>
    </article>
  )
}

function ProductDetailSkeleton() {
  const { t } = useTranslation('catalog')
  return (
    <div className="grid gap-8 lg:grid-cols-2" role="status" aria-live="polite">
      <p className="sr-only">{t('detail.loadingSr')}</p>
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
  const { t } = useTranslation('catalog')
  return (
    <div className="rounded-2xl border border-dashed border-border bg-card px-6 py-10 text-center">
      <h1 className="text-xl font-semibold text-foreground">{title}</h1>
      <p className="mt-2 text-sm text-muted-foreground">{message}</p>
      <Button asChild variant="link" className="mt-4">
        <Link to="/products">{t('detail.backToCatalog')}</Link>
      </Button>
    </div>
  )
}
