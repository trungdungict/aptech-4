import { useQuery } from '@tanstack/react-query'
import { MenuIcon, SearchIcon, ShoppingCartIcon } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Separator } from '@/components/ui/separator'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { useAuth } from '@/features/auth/hooks/useAuth'
import * as cartApi from '@/features/cart/api/cart-api'
import { cartQueryKeys } from '@/features/cart/hooks/cart-query-keys'
import { listCategories } from '@/features/catalog/api/catalog-api'
import { catalogQueryKeys } from '@/features/catalog/hooks/catalog-query-keys'
import { resolveCategoryIcon } from '@/features/catalog/lib/resolve-product-image'
import { LazyChatPanel } from '@/features/chat/components/LazyChatPanel'
import { NotificationBell } from '@/features/notifications/components/NotificationBell'
import { useMediaQuery } from '@/hooks/use-media-query'
import { navLinkClass } from '@/lib/nav-link-class'

const LOGO_SRC = '/assets/foodmart/images/misc/logo.png'

export function RootLayout() {
  const { status, user, logout } = useAuth()
  const navigate = useNavigate()
  const isDesktop = useMediaQuery('(min-width: 768px)', true)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [searchQ, setSearchQ] = useState('')
  const authenticated = status === 'authenticated' && user != null

  const categoriesQuery = useQuery({
    queryKey: catalogQueryKeys.categories(),
    queryFn: ({ signal }) => listCategories(signal),
    staleTime: 60_000,
  })

  const cartQuery = useQuery({
    queryKey: cartQueryKeys.detail(user?.id ?? 'anonymous'),
    queryFn: ({ signal }) => cartApi.getCart(signal),
    enabled: authenticated && Boolean(user?.id),
    staleTime: 30_000,
    retry: false,
  })

  const cartCount =
    cartQuery.data?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0

  const closeMobile = () => setMobileOpen(false)

  const onSearch = (event: FormEvent) => {
    event.preventDefault()
    const q = searchQ.trim()
    closeMobile()
    void navigate(q ? `/products?q=${encodeURIComponent(q)}` : '/products')
  }

  const searchForm = (
    <form
      onSubmit={onSearch}
      className="flex w-full max-w-xl items-center gap-2"
      role="search"
      aria-label="Product search"
    >
      <div className="relative min-w-0 flex-1">
        <SearchIcon
          className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden="true"
        />
        <Input
          value={searchQ}
          onChange={(event) => setSearchQ(event.target.value)}
          placeholder="Search groceries…"
          className="h-10 border-border bg-background pl-9"
          aria-label="Search products"
        />
      </div>
      <Button type="submit" size="default" className="shrink-0">
        Search
      </Button>
    </form>
  )

  const catalogLink = (
    <NavLink
      to="/products"
      className={({ isActive }) => navLinkClass(isActive)}
      onClick={closeMobile}
    >
      Catalog
    </NavLink>
  )

  const cartLink = (
    <NavLink
      to="/cart"
      className={({ isActive }) =>
        `${navLinkClass(isActive)} inline-flex items-center gap-1.5`
      }
      onClick={closeMobile}
      aria-label={cartCount > 0 ? `Cart, ${cartCount} items` : 'Cart'}
    >
      <ShoppingCartIcon className="size-4" aria-hidden="true" />
      <span>Cart</span>
      {authenticated && cartCount > 0 ? (
        <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-[11px] font-semibold text-primary-foreground tabular-nums">
          {cartCount > 99 ? '99+' : cartCount}
        </span>
      ) : null}
    </NavLink>
  )

  const authLinks = status === 'booting' ? (
    <span className="px-3 py-2 text-sm text-muted-foreground" role="status">
      Loading…
    </span>
  ) : authenticated ? (
    <>
      {cartLink}
      <NavLink
        to="/orders"
        className={({ isActive }) => navLinkClass(isActive)}
        onClick={closeMobile}
      >
        Orders
      </NavLink>
    </>
  ) : (
    <>
      <NavLink
        to="/login"
        className={({ isActive }) => navLinkClass(isActive)}
        onClick={closeMobile}
      >
        Sign in
      </NavLink>
      <Button asChild size="sm">
        <Link to="/register" onClick={closeMobile}>
          Register
        </Link>
      </Button>
    </>
  )

  const categories = categoriesQuery.data ?? []

  return (
    <div className="flex min-h-screen flex-col bg-background">
      <div className="border-b border-border/70 bg-[#1d2b2a] text-[11px] text-white/85 sm:text-xs">
        <div className="mx-auto flex w-full max-w-7xl flex-wrap items-center justify-between gap-x-4 gap-y-1 px-4 py-2 sm:px-6">
          <p>Aptech-4 grocery storefront · Live catalog from the API</p>
          <p className="text-white/70">Demo checkout only — not a live payment gateway</p>
        </div>
      </div>

      <header className="sticky top-0 z-40 border-b border-border/80 bg-card/95 backdrop-blur">
        <div className="mx-auto flex w-full max-w-7xl flex-col gap-3 px-4 py-3 sm:px-6">
          <div className="flex items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-2">
              {!isDesktop ? (
                <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    aria-label="Open menu"
                    onClick={() => setMobileOpen(true)}
                  >
                    <MenuIcon className="size-4" />
                  </Button>
                  <SheetContent side="left" className="w-[min(100%,20rem)] p-0 sm:max-w-xs">
                    <SheetHeader className="border-b border-border p-4 text-left">
                      <SheetTitle>Menu</SheetTitle>
                      <SheetDescription>Grocery storefront navigation</SheetDescription>
                    </SheetHeader>
                    <div className="space-y-3 p-3">
                      {searchForm}
                      <nav className="flex flex-col gap-1" aria-label="Storefront menu">
                        {catalogLink}
                        {authLinks}
                        {authenticated ? (
                          <>
                            <Separator className="my-2" />
                            <p className="truncate px-3 text-sm text-muted-foreground">
                              {user.fullName}
                            </p>
                            <Button
                              type="button"
                              variant="outline"
                              className="mx-3 justify-start"
                              onClick={() => {
                                closeMobile()
                                void logout()
                              }}
                            >
                              Sign out
                            </Button>
                          </>
                        ) : null}
                      </nav>
                      {categories.length > 0 ? (
                        <div className="space-y-1 border-t border-border pt-3">
                          <p className="px-3 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                            Categories
                          </p>
                          <ul className="flex flex-col gap-0.5">
                            {categories.map((category) => (
                              <li key={category.id}>
                                <Link
                                  to={`/products?categoryId=${encodeURIComponent(category.id)}`}
                                  className="flex items-center gap-2 rounded-md px-3 py-2 text-sm hover:bg-accent"
                                  onClick={closeMobile}
                                >
                                  <img
                                    src={resolveCategoryIcon(category.name)}
                                    alt=""
                                    className="size-5 object-contain"
                                    loading="lazy"
                                  />
                                  {category.name}
                                </Link>
                              </li>
                            ))}
                          </ul>
                        </div>
                      ) : null}
                    </div>
                  </SheetContent>
                </Sheet>
              ) : null}

              <Link to="/" className="flex min-w-0 items-center gap-2" onClick={closeMobile}>
                <img
                  src={LOGO_SRC}
                  alt="FoodMart"
                  className="h-8 w-auto max-w-[9.5rem] object-contain sm:h-9"
                  width={241}
                  height={54}
                />
                <span className="sr-only">Aptech-4 home</span>
              </Link>
            </div>

            {isDesktop ? (
              <div className="mx-4 hidden min-w-0 flex-1 md:block">{searchForm}</div>
            ) : null}

            {isDesktop ? (
              <nav className="flex shrink-0 items-center gap-1" aria-label="Storefront">
                {catalogLink}
                {authLinks}
                {authenticated ? (
                  <>
                    <NotificationBell key={user.id} />
                    <span className="hidden max-w-[10rem] truncate px-2 text-sm text-muted-foreground lg:inline">
                      {user.fullName}
                    </span>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => {
                        void logout()
                      }}
                    >
                      Sign out
                    </Button>
                  </>
                ) : null}
              </nav>
            ) : (
              <div className="flex shrink-0 items-center gap-2">
                {authenticated ? (
                  <>
                    <NotificationBell key={user.id} />
                    <Link
                      to="/cart"
                      className="relative inline-flex size-9 items-center justify-center rounded-md border border-border bg-card"
                      aria-label={cartCount > 0 ? `Cart, ${cartCount} items` : 'Cart'}
                    >
                      <ShoppingCartIcon className="size-4" />
                      {cartCount > 0 ? (
                        <span className="absolute -top-1 -right-1 inline-flex min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold text-primary-foreground tabular-nums">
                          {cartCount > 99 ? '99+' : cartCount}
                        </span>
                      ) : null}
                    </Link>
                  </>
                ) : null}
                {!authenticated && status !== 'booting' ? (
                  <Button asChild size="sm" variant="default">
                    <Link to="/register">Register</Link>
                  </Button>
                ) : null}
              </div>
            )}
          </div>

          {!isDesktop ? <div className="md:hidden">{searchForm}</div> : null}
        </div>

        {categories.length > 0 ? (
          <nav
            className="hidden border-t border-border/70 bg-[#f7faf8] md:block"
            aria-label="Product categories"
          >
            <ul className="mx-auto flex w-full max-w-7xl gap-1 overflow-x-auto px-4 py-2 sm:px-6">
              {categories.map((category) => (
                <li key={category.id} className="shrink-0">
                  <Link
                    to={`/products?categoryId=${encodeURIComponent(category.id)}`}
                    className="inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-sm text-foreground transition hover:bg-accent hover:text-accent-foreground"
                  >
                    <img
                      src={resolveCategoryIcon(category.name)}
                      alt=""
                      className="size-5 object-contain"
                      loading="lazy"
                    />
                    {category.name}
                  </Link>
                </li>
              ))}
            </ul>
          </nav>
        ) : null}
      </header>

      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>

      <footer className="mt-auto border-t border-border/80 bg-[#1d2b2a] text-white/80">
        <div className="mx-auto grid w-full max-w-7xl gap-8 px-4 py-10 sm:px-6 md:grid-cols-3">
          <div className="space-y-3">
            <img
              src={LOGO_SRC}
              alt=""
              className="h-8 w-auto brightness-0 invert"
              width={241}
              height={54}
            />
            <p className="max-w-sm text-sm text-white/70">
              Aptech-4 customer storefront with a FoodMart-inspired grocery look. Catalog, cart, and
              orders use the live API; payment remains demo-only.
            </p>
          </div>
          <div className="space-y-2 text-sm">
            <p className="font-semibold text-white">Shop</p>
            <ul className="space-y-1.5 text-white/75">
              <li>
                <Link className="hover:text-white" to="/products">
                  Catalog
                </Link>
              </li>
              <li>
                <Link className="hover:text-white" to="/cart">
                  Cart
                </Link>
              </li>
              <li>
                <Link className="hover:text-white" to="/orders">
                  Orders
                </Link>
              </li>
            </ul>
          </div>
          <div className="space-y-2 text-sm">
            <p className="font-semibold text-white">Account</p>
            <ul className="space-y-1.5 text-white/75">
              <li>
                <Link className="hover:text-white" to="/login">
                  Sign in
                </Link>
              </li>
              <li>
                <Link className="hover:text-white" to="/register">
                  Register
                </Link>
              </li>
            </ul>
          </div>
        </div>
        <div className="border-t border-white/10">
          <div className="mx-auto flex w-full max-w-7xl flex-col gap-2 px-4 py-4 text-center text-xs text-white/60 sm:flex-row sm:items-center sm:justify-between sm:px-6 sm:text-left">
            <p>Customer cart, mock checkout, and orders — payment is demo-only.</p>
            <p>
              Free HTML Template by{' '}
              <a
                className="underline underline-offset-2 hover:text-white"
                href="https://templatesjungle.com/"
                target="_blank"
                rel="noreferrer"
              >
                TemplatesJungle
              </a>{' '}
              Distributed by{' '}
              <a
                className="underline underline-offset-2 hover:text-white"
                href="https://themewagon.com/themes/foodmart/"
                target="_blank"
                rel="noreferrer"
              >
                ThemeWagon
              </a>
            </p>
          </div>
        </div>
      </footer>

      {authenticated ? <LazyChatPanel key={user.id} variant="customer" /> : null}
    </div>
  )
}
