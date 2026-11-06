import { useQuery } from '@tanstack/react-query'
import {
  ArrowRightIcon,
  CreditCardIcon,
  LeafIcon,
  PackageIcon,
  ShoppingBasketIcon,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { hasAdminRole } from '@/features/auth/lib/roles'
import { useAuth } from '@/features/auth/hooks/useAuth'
import { listCategories, listProducts } from '@/features/catalog/api/catalog-api'
import { ProductGrid } from '@/features/catalog/components/ProductGrid'
import { catalogQueryKeys } from '@/features/catalog/hooks/catalog-query-keys'
import { resolveCategoryIcon } from '@/features/catalog/lib/resolve-product-image'
import type { Product } from '@/features/catalog/types'
import { useAppLocale } from '@/features/i18n/hooks/useAppLocale'
import { HomePromotionShowcase } from '@/features/promotions/components/HomePromotionShowcase'

const AD_3 = '/assets/foodmart/images/banners/ad-image-3.png'
const AD_4 = '/assets/foodmart/images/banners/ad-image-4.png'
const SLIDE_1 = '/assets/foodmart/images/banners/slide-1.jpg'
const SLIDE_2 = '/assets/foodmart/images/banners/slide-2.jpg'
const PATTERN = '/assets/foodmart/images/misc/bg-leaves-img-pattern.png'

/**
 * Grocery-style landing — live catalog APIs only; no fabricated ratings, discounts, or brands.
 */
export function HomePage() {
  const { t } = useTranslation('home')
  const { t: tc } = useTranslation('common')
  const { status, user } = useAuth()
  const { locale } = useAppLocale()

  const categoriesQuery = useQuery({
    queryKey: catalogQueryKeys.categories(locale),
    queryFn: ({ signal }) => listCategories(signal),
    staleTime: 60_000,
  })

  const trendingQuery = useQuery({
    queryKey: catalogQueryKeys.productList(locale, { page: 0, size: 8, sort: 'name,asc' }),
    queryFn: ({ signal }) => listProducts({ page: 0, size: 8, sort: 'name,asc' }, signal),
    staleTime: 60_000,
    retry: false,
  })

  const featuredQuery = useQuery({
    queryKey: catalogQueryKeys.productList(locale, { page: 0, size: 8, sort: 'price,asc' }),
    queryFn: ({ signal }) => listProducts({ page: 0, size: 8, sort: 'price,asc' }, signal),
    staleTime: 60_000,
    retry: false,
  })

  const justArrivedQuery = useQuery({
    queryKey: catalogQueryKeys.productList(locale, { page: 0, size: 8, sort: 'createdAt,desc' }),
    queryFn: ({ signal }) => listProducts({ page: 0, size: 8, sort: 'createdAt,desc' }, signal),
    staleTime: 60_000,
    retry: false,
  })

  const categories = categoriesQuery.data ?? []
  const trending = trendingQuery.data?.items ?? []
  const featured = featuredQuery.data?.items ?? []
  const justArrived = justArrivedQuery.data?.items ?? []

  return (
    <div className="space-y-12 md:space-y-16">
      <section className="relative overflow-hidden rounded-2xl border border-border/60 bg-[#e8f2ee]">
        <div
          className="pointer-events-none absolute inset-0 opacity-40"
          style={{
            backgroundImage: `url(${PATTERN})`,
            backgroundSize: 'cover',
            backgroundPosition: 'right center',
          }}
          aria-hidden="true"
        />
        <div className="relative space-y-5 p-6 sm:p-8 lg:max-w-3xl lg:p-10">
          <p className="text-sm font-semibold tracking-wide text-primary uppercase">
            {t('hero.eyebrow')}
          </p>
          <h1 className="max-w-xl text-3xl font-bold tracking-tight text-foreground sm:text-4xl md:text-5xl">
            {t('hero.title')}
          </h1>
          <p className="max-w-prose text-base text-muted-foreground md:text-lg">
            {t('hero.body')}
          </p>
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <Link to="/products">
                {t('hero.browse')}
                <ArrowRightIcon className="size-4" />
              </Link>
            </Button>
            {status === 'anonymous' ? (
              <>
                <Button asChild variant="outline" size="lg">
                  <Link to="/login">{tc('nav.signIn')}</Link>
                </Button>
                <Button asChild variant="secondary" size="lg">
                  <Link to="/register">{tc('nav.register')}</Link>
                </Button>
              </>
            ) : null}
            {status === 'authenticated' && user ? (
              <p className="self-center text-sm text-muted-foreground">
                {t('hero.signedInAs')}{' '}
                <span className="font-medium text-foreground">{user.fullName}</span>
                {hasAdminRole(user.roles) ? (
                  <>
                    {' · '}
                    <Link
                      className="font-medium text-primary underline-offset-4 hover:underline"
                      to="/admin"
                    >
                      {tc('nav.admin')}
                    </Link>
                  </>
                ) : null}
              </p>
            ) : null}
            {status === 'booting' ? (
              <p className="self-center text-sm text-muted-foreground" role="status">
                {t('hero.restoring')}
              </p>
            ) : null}
          </div>
        </div>
      </section>

      <HomePromotionShowcase />

      <section className="space-y-4" aria-labelledby="home-categories-heading">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 id="home-categories-heading" className="text-2xl font-semibold text-foreground">
              {t('categories.title')}
            </h2>
            <p className="text-sm text-muted-foreground">{t('categories.subtitle')}</p>
          </div>
          <Button asChild variant="outline" size="sm">
            <Link to="/products">{t('categories.viewAll')}</Link>
          </Button>
        </div>

        {categoriesQuery.isLoading ? (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6" aria-busy="true">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-28 w-full rounded-xl" />
            ))}
          </div>
        ) : categoriesQuery.isError ? (
          <p className="text-sm text-muted-foreground" role="status">
            {t('categories.loadError')}
          </p>
        ) : categories.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('categories.empty')}</p>
        ) : (
          <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
            {categories.map((category) => (
              <li key={category.id}>
                <Link
                  to={`/products?categoryId=${encodeURIComponent(category.id)}`}
                  className="flex h-full flex-col items-center gap-3 rounded-xl border border-border/80 bg-card px-3 py-5 text-center transition hover:border-primary/40 hover:bg-accent/40 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                >
                  <img
                    src={resolveCategoryIcon(category.name)}
                    alt=""
                    className="size-10 object-contain"
                    loading="lazy"
                  />
                  <p className="text-sm font-semibold text-foreground">{category.name}</p>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <ProductSection
        headingId="home-trending-heading"
        title={t('sections.trending')}
        subtitle={t('sections.trendingSub')}
        products={trending}
        query={trendingQuery}
        viewAllTo="/products?sort=name,asc"
      />

      <ProductSection
        headingId="home-best-heading"
        title={t('sections.bestSelling')}
        subtitle={t('sections.bestSellingSub')}
        products={featured}
        query={featuredQuery}
        viewAllTo="/products?sort=price,asc"
      />

      <section className="space-y-4" aria-labelledby="home-promo-heading">
        <div>
          <h2 id="home-promo-heading" className="text-2xl font-semibold text-foreground">
            {t('sections.seasonal')}
          </h2>
          <p className="text-sm text-muted-foreground">
            {t('sections.seasonalSub')}
          </p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[
            { src: AD_3, label: t('gallery.bakery') },
            { src: AD_4, label: t('gallery.dairy') },
            { src: SLIDE_1, label: t('gallery.weekly') },
            { src: SLIDE_2, label: t('gallery.kitchen') },
          ].map((item) => (
            <Link
              key={item.src}
              to="/products"
              className="group overflow-hidden rounded-xl border border-border/70 bg-card"
            >
              <div className="aspect-[4/3] overflow-hidden bg-[#f4f7f5]">
                <img
                  src={item.src}
                  alt=""
                  className="h-full w-full object-cover transition duration-300 group-hover:scale-[1.03]"
                  loading="lazy"
                />
              </div>
              <p className="px-3 py-2.5 text-sm font-medium text-foreground">{item.label}</p>
            </Link>
          ))}
        </div>
      </section>

      <ProductSection
        headingId="home-arrived-heading"
        title={t('sections.justArrived')}
        subtitle={t('sections.justArrivedSub')}
        products={justArrived}
        query={justArrivedQuery}
        viewAllTo="/products?sort=createdAt,desc"
      />

      <section
        className="rounded-2xl border border-border/70 bg-card px-5 py-8 sm:px-8"
        aria-labelledby="home-benefits-heading"
      >
        <h2 id="home-benefits-heading" className="text-2xl font-semibold text-foreground">
          {t('benefits.title')}
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          {t('benefits.subtitle')}
        </p>
        <ul className="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <Benefit
            icon={<LeafIcon className="size-5 text-primary" aria-hidden="true" />}
            title={t('benefits.catalogTitle')}
            body={t('benefits.catalogBody')}
          />
          <Benefit
            icon={<ShoppingBasketIcon className="size-5 text-primary" aria-hidden="true" />}
            title={t('benefits.cartTitle')}
            body={t('benefits.cartBody')}
          />
          <Benefit
            icon={<CreditCardIcon className="size-5 text-primary" aria-hidden="true" />}
            title={t('benefits.checkoutTitle')}
            body={t('benefits.checkoutBody')}
          />
          <Benefit
            icon={<PackageIcon className="size-5 text-primary" aria-hidden="true" />}
            title={t('benefits.ordersTitle')}
            body={t('benefits.ordersBody')}
          />
        </ul>
      </section>
    </div>
  )
}

function ProductSection({
  headingId,
  title,
  subtitle,
  products,
  query,
  viewAllTo,
}: {
  headingId: string
  title: string
  subtitle: string
  products: Product[]
  query: { isLoading: boolean; isError: boolean }
  viewAllTo: string
}) {
  const { t } = useTranslation('home')
  return (
    <section className="space-y-4" aria-labelledby={headingId}>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id={headingId} className="text-2xl font-semibold text-foreground">
            {title}
          </h2>
          <p className="text-sm text-muted-foreground">{subtitle}</p>
        </div>
        <Button asChild variant="link" className="h-auto p-0">
          <Link to={viewAllTo}>
            {t('sections.viewAll')}
            <ArrowRightIcon className="size-4" />
          </Link>
        </Button>
      </div>

      {query.isLoading ? (
        <div role="status" aria-live="polite">
          <p className="sr-only">{t('sections.loading')}</p>
          <div className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-3 xl:grid-cols-4">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} className="aspect-[4/3] w-full rounded-xl" />
            ))}
          </div>
        </div>
      ) : query.isError ? (
        <p className="text-sm text-muted-foreground" role="status">
          {t('sections.loadError')}
        </p>
      ) : products.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('sections.empty')}</p>
      ) : (
        <ProductGrid products={products} />
      )}
    </section>
  )
}

function Benefit({
  icon,
  title,
  body,
}: {
  icon: ReactNode
  title: string
  body: string
}) {
  return (
    <li className="space-y-2">
      <div className="inline-flex size-10 items-center justify-center rounded-full bg-accent">{icon}</div>
      <p className="font-semibold text-foreground">{title}</p>
      <p className="text-sm text-muted-foreground">{body}</p>
    </li>
  )
}
