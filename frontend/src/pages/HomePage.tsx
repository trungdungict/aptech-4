import { useQuery } from '@tanstack/react-query'
import {
  ArrowRightIcon,
  CreditCardIcon,
  LeafIcon,
  PackageIcon,
  ShoppingBasketIcon,
} from 'lucide-react'
import type { ReactNode } from 'react'
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

const HERO_IMG = '/assets/foodmart/images/banners/banner-image-1.jpg'
const AD_1 = '/assets/foodmart/images/banners/ad-image-1.png'
const AD_2 = '/assets/foodmart/images/banners/ad-image-2.png'
const AD_3 = '/assets/foodmart/images/banners/ad-image-3.png'
const AD_4 = '/assets/foodmart/images/banners/ad-image-4.png'
const SLIDE_1 = '/assets/foodmart/images/banners/slide-1.jpg'
const SLIDE_2 = '/assets/foodmart/images/banners/slide-2.jpg'
const PATTERN = '/assets/foodmart/images/misc/bg-leaves-img-pattern.png'

/**
 * Grocery-style landing — live catalog APIs only; no fabricated ratings, discounts, or brands.
 */
export function HomePage() {
  const { status, user } = useAuth()

  const categoriesQuery = useQuery({
    queryKey: catalogQueryKeys.categories(),
    queryFn: ({ signal }) => listCategories(signal),
    staleTime: 60_000,
  })

  const trendingQuery = useQuery({
    queryKey: catalogQueryKeys.productList({ page: 0, size: 8, sort: 'name,asc' }),
    queryFn: ({ signal }) => listProducts({ page: 0, size: 8, sort: 'name,asc' }, signal),
    staleTime: 60_000,
    retry: false,
  })

  const featuredQuery = useQuery({
    queryKey: catalogQueryKeys.productList({ page: 0, size: 8, sort: 'price,asc' }),
    queryFn: ({ signal }) => listProducts({ page: 0, size: 8, sort: 'price,asc' }, signal),
    staleTime: 60_000,
    retry: false,
  })

  const justArrivedQuery = useQuery({
    queryKey: catalogQueryKeys.productList({ page: 0, size: 8, sort: 'createdAt,desc' }),
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
        <div className="relative grid items-center gap-6 p-6 sm:p-8 lg:grid-cols-[1.05fr_0.95fr] lg:gap-10 lg:p-10">
          <div className="space-y-5">
            <p className="text-sm font-semibold tracking-wide text-primary uppercase">
              Fresh grocery shopping
            </p>
            <h1 className="max-w-xl text-3xl font-bold tracking-tight text-foreground sm:text-4xl md:text-5xl">
              Stock your kitchen from the live Aptech-4 catalog
            </h1>
            <p className="max-w-prose text-base text-muted-foreground md:text-lg">
              Browse categories, check real prices and stock, then sign in to cart and complete the
              demo checkout.
            </p>
            <div className="flex flex-wrap gap-3">
              <Button asChild size="lg">
                <Link to="/products">
                  Browse catalog
                  <ArrowRightIcon className="size-4" />
                </Link>
              </Button>
              {status === 'anonymous' ? (
                <>
                  <Button asChild variant="outline" size="lg">
                    <Link to="/login">Sign in</Link>
                  </Button>
                  <Button asChild variant="secondary" size="lg">
                    <Link to="/register">Register</Link>
                  </Button>
                </>
              ) : null}
              {status === 'authenticated' && user ? (
                <p className="self-center text-sm text-muted-foreground">
                  Signed in as{' '}
                  <span className="font-medium text-foreground">{user.fullName}</span>
                  {hasAdminRole(user.roles) ? (
                    <>
                      {' · '}
                      <Link
                        className="font-medium text-primary underline-offset-4 hover:underline"
                        to="/admin"
                      >
                        Admin
                      </Link>
                    </>
                  ) : null}
                </p>
              ) : null}
              {status === 'booting' ? (
                <p className="self-center text-sm text-muted-foreground" role="status">
                  Restoring session…
                </p>
              ) : null}
            </div>
          </div>
          <div className="relative min-h-[14rem] overflow-hidden rounded-xl sm:min-h-[18rem]">
            <img
              src={HERO_IMG}
              alt="Fresh produce and grocery ingredients"
              className="absolute inset-0 h-full w-full object-cover"
              width={960}
              height={720}
              fetchPriority="high"
              decoding="async"
            />
          </div>
        </div>
      </section>

      <section className="space-y-4" aria-labelledby="home-categories-heading">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 id="home-categories-heading" className="text-2xl font-semibold text-foreground">
              Grocery categories
            </h2>
            <p className="text-sm text-muted-foreground">Active categories from the public API.</p>
          </div>
          <Button asChild variant="outline" size="sm">
            <Link to="/products">View all products</Link>
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
            Categories could not be loaded. You can still browse the full catalog.
          </p>
        ) : categories.length === 0 ? (
          <p className="text-sm text-muted-foreground">No active categories are available yet.</p>
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
        title="Trending products"
        subtitle="Live catalog · sorted by name"
        products={trending}
        query={trendingQuery}
        viewAllTo="/products?sort=name,asc"
      />

      <ProductSection
        headingId="home-best-heading"
        title="Best-selling products"
        subtitle="Live catalog · sorted by price (sales rankings are not available yet)"
        products={featured}
        query={featuredQuery}
        viewAllTo="/products?sort=price,asc"
      />

      <section className="space-y-4" aria-labelledby="home-promo-heading">
        <div>
          <h2 id="home-promo-heading" className="text-2xl font-semibold text-foreground">
            Seasonal inspiration
          </h2>
          <p className="text-sm text-muted-foreground">
            Decorative grocery artwork — not a priced promotion.
          </p>
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          <PromoBanner
            to="/products"
            image={AD_1}
            title="Fresh from the produce aisle"
            caption="Explore fruits, vegetables, and pantry staples in the catalog."
            tone="mint"
          />
          <PromoBanner
            to="/products"
            image={AD_2}
            title="Stock up for the week"
            caption="Browse active products with server-side prices and stock."
            tone="cream"
          />
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {[
            { src: AD_3, label: 'Bakery & sweets' },
            { src: AD_4, label: 'Dairy & drinks' },
            { src: SLIDE_1, label: 'Weekly grocery haul' },
            { src: SLIDE_2, label: 'Kitchen essentials' },
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
        title="Just arrived"
        subtitle="Newest active products from the API"
        products={justArrived}
        query={justArrivedQuery}
        viewAllTo="/products?sort=createdAt,desc"
      />

      <section
        className="rounded-2xl border border-border/70 bg-card px-5 py-8 sm:px-8"
        aria-labelledby="home-benefits-heading"
      >
        <h2 id="home-benefits-heading" className="text-2xl font-semibold text-foreground">
          Why shop here
        </h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Storefront capabilities backed by the Aptech-4 APIs — no invented delivery promises.
        </p>
        <ul className="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <Benefit
            icon={<LeafIcon className="size-5 text-primary" aria-hidden="true" />}
            title="Grocery-focused catalog"
            body="Categories and products served from the live public API."
          />
          <Benefit
            icon={<ShoppingBasketIcon className="size-5 text-primary" aria-hidden="true" />}
            title="Cart when you sign in"
            body="Add lines, adjust quantities, and review subtotals before checkout."
          />
          <Benefit
            icon={<CreditCardIcon className="size-5 text-primary" aria-hidden="true" />}
            title="Demo checkout"
            body="Mock payment flow for learning — not a live payment gateway."
          />
          <Benefit
            icon={<PackageIcon className="size-5 text-primary" aria-hidden="true" />}
            title="Order history"
            body="Track your own orders after a successful demo purchase."
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
            View all
            <ArrowRightIcon className="size-4" />
          </Link>
        </Button>
      </div>

      {query.isLoading ? (
        <div role="status" aria-live="polite">
          <p className="sr-only">Loading products…</p>
          <div className="grid grid-cols-2 gap-3 sm:gap-4 md:grid-cols-3 xl:grid-cols-4">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} className="aspect-[4/3] w-full rounded-xl" />
            ))}
          </div>
        </div>
      ) : query.isError ? (
        <p className="text-sm text-muted-foreground" role="status">
          Products could not be loaded. Open the catalog to browse everything.
        </p>
      ) : products.length === 0 ? (
        <p className="text-sm text-muted-foreground">No products are available in the catalog yet.</p>
      ) : (
        <ProductGrid products={products} />
      )}
    </section>
  )
}

function PromoBanner({
  to,
  image,
  title,
  caption,
  tone,
}: {
  to: string
  image: string
  title: string
  caption: string
  tone: 'mint' | 'cream'
}) {
  return (
    <Link
      to={to}
      className={`flex flex-col overflow-hidden rounded-xl border border-border/70 sm:flex-row ${
        tone === 'mint' ? 'bg-[#e7f4ee]' : 'bg-[#f7f1e8]'
      }`}
    >
      <div className="flex flex-1 flex-col justify-center gap-2 p-5 sm:p-6">
        <p className="text-lg font-semibold text-foreground">{title}</p>
        <p className="text-sm text-muted-foreground">{caption}</p>
        <span className="inline-flex items-center gap-1 text-sm font-medium text-primary">
          Shop catalog
          <ArrowRightIcon className="size-3.5" />
        </span>
      </div>
      <div className="relative h-40 shrink-0 sm:h-auto sm:w-44 md:w-52">
        <img
          src={image}
          alt=""
          className="h-full w-full object-contain object-bottom p-2"
          loading="lazy"
        />
      </div>
    </Link>
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
