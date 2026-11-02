import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes, useSearchParams } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { LoginPage } from '@/features/auth/pages/LoginPage'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { demoCustomerSession, renderWithProviders } from '@/test/render-app'

import type { Product } from '../types'
import { ProductCard } from './ProductCard'

const baseProduct: Product = {
  id: '11111111-1111-1111-1111-111111111111',
  sku: 'FOOD-FRUIT-BANANA-001',
  name: 'Yellow Bananas',
  description: null,
  unitPrice: '25000',
  currency: 'VND',
  categoryId: '22222222-2222-2222-2222-222222222222',
  categoryName: 'Fruits & Vegetables',
  active: true,
  imageUrl: null,
  inStock: true,
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function NextProbe() {
  const [params] = useSearchParams()
  return <div data-testid="login-next">{params.get('next')}</div>
}

describe('ProductCard', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('renders grocery card with resolved FoodMart thumbnail and detail links', () => {
    renderWithProviders(<ProductCard product={baseProduct} />)

    expect(screen.getByRole('link', { name: /view yellow bananas/i })).toHaveAttribute(
      'href',
      `/products/${baseProduct.id}`,
    )
    expect(screen.getByRole('link', { name: /^yellow bananas$/i })).toHaveAttribute(
      'href',
      `/products/${baseProduct.id}`,
    )
    expect(screen.getByText('Fruits & Vegetables')).toBeInTheDocument()
    const img = document.querySelector('img[src*="thumb-bananas.png"]')
    expect(img).not.toBeNull()
    expect(img).toHaveAttribute('loading', 'lazy')
  })

  it('shows a neutral placeholder when no SKU or name match exists', () => {
    renderWithProviders(
      <ProductCard
        product={{
          ...baseProduct,
          sku: 'UNKNOWN-SKU',
          name: 'Mystery Widget',
          imageUrl: null,
        }}
      />,
    )

    expect(screen.getByText(/no image/i)).toBeInTheDocument()
  })

  it('shows quantity controls defaulting to 1 for authenticated customers', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      if (String(input).includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<ProductCard product={baseProduct} />, {
      session: demoCustomerSession,
    })

    expect(screen.getByTestId(`product-card-qty-${baseProduct.id}`)).toHaveTextContent('1')
    expect(
      screen.getByRole('button', { name: /decrease quantity for yellow bananas/i }),
    ).toBeDisabled()
    // Add stays disabled while AuthProvider status === 'booting'; wait for restore, not just mount.
    const addButton = await screen.findByRole('button', {
      name: /add yellow bananas to cart/i,
    })
    await waitFor(() => {
      expect(addButton).toBeEnabled()
    })
    expect(
      screen.getByRole('button', { name: /increase quantity for yellow bananas/i }),
    ).toBeEnabled()
  })

  it('does not reduce quantity below 1', async () => {
    const user = userEvent.setup()
    renderWithProviders(<ProductCard product={baseProduct} />, {
      session: demoCustomerSession,
    })

    const decrease = screen.getByRole('button', {
      name: /decrease quantity for yellow bananas/i,
    })
    const increase = screen.getByRole('button', {
      name: /increase quantity for yellow bananas/i,
    })
    const quantity = screen.getByTestId(`product-card-qty-${baseProduct.id}`)

    expect(decrease).toBeDisabled()
    await user.click(increase)
    expect(quantity).toHaveTextContent('2')
    expect(decrease).toBeEnabled()

    await user.click(decrease)
    expect(quantity).toHaveTextContent('1')
    expect(decrease).toBeDisabled()
  })

  it('disables quantity and add controls when the product is out of stock', () => {
    renderWithProviders(<ProductCard product={{ ...baseProduct, inStock: false }} />, {
      session: demoCustomerSession,
    })

    expect(
      screen.getByRole('button', { name: /decrease quantity for yellow bananas/i }),
    ).toBeDisabled()
    expect(
      screen.getByRole('button', { name: /increase quantity for yellow bananas/i }),
    ).toBeDisabled()
    expect(screen.getByRole('button', { name: /add yellow bananas to cart/i })).toBeDisabled()
  })

  it('adds the selected quantity via the existing cart API for authenticated customers', async () => {
    const user = userEvent.setup()
    const cartPosts: unknown[] = []

    vi.mocked(fetch).mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      if (url.includes('/cart/items') && init?.method === 'POST') {
        cartPosts.push(JSON.parse(String(init.body)))
        return jsonResponse(200, {
          id: 'cart-1',
          items: [],
          subtotal: 0,
          currency: 'VND',
          hasUnavailableItems: false,
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<ProductCard product={baseProduct} />, {
      route: '/products?q=banana',
      session: demoCustomerSession,
    })

    await user.click(
      screen.getByRole('button', { name: /increase quantity for yellow bananas/i }),
    )
    await user.click(screen.getByRole('button', { name: /add yellow bananas to cart/i }))

    await waitFor(() => {
      expect(cartPosts).toEqual([{ productId: baseProduct.id, quantity: 2 }])
    })
    expect(await screen.findByText(/added to cart/i)).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
  })

  it('redirects guests to login with a safe next path and does not call the cart API', async () => {
    const user = userEvent.setup()
    const cartPosts: string[] = []

    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/cart/items')) {
        cartPosts.push(url)
      }
      return jsonResponse(401, { status: 401, error: 'Unauthorized', message: 'login' })
    })

    renderWithProviders(
      <Routes>
        <Route path="/products" element={<ProductCard product={baseProduct} />} />
        <Route
          path="/login"
          element={
            <>
              <LoginPage />
              <NextProbe />
            </>
          }
        />
      </Routes>,
      { route: '/products?q=banana' },
    )

    await user.click(screen.getByRole('button', { name: /add yellow bananas to cart/i }))

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument()
    expect(screen.getByTestId('login-next')).toHaveTextContent('/products?q=banana')
    expect(cartPosts).toHaveLength(0)
  })

  it('keeps Add to Cart from navigating to product detail', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      if (url.includes('/cart/items') && init?.method === 'POST') {
        return jsonResponse(200, {
          id: 'cart-1',
          items: [],
          subtotal: 0,
          currency: 'VND',
          hasUnavailableItems: false,
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(
      <Routes>
        <Route path="/products" element={<ProductCard product={baseProduct} />} />
        <Route
          path="/products/:productId"
          element={<h1>Product detail page should not open</h1>}
        />
      </Routes>,
      { route: '/products', session: demoCustomerSession },
    )

    await user.click(screen.getByRole('button', { name: /add yellow bananas to cart/i }))
    expect(await screen.findByText(/added to cart/i)).toBeInTheDocument()
    expect(screen.queryByText(/product detail page should not open/i)).not.toBeInTheDocument()
  })

  it('shows cart errors from the API without leaving the card', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockImplementation(async (input, init) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      if (url.includes('/cart/items') && init?.method === 'POST') {
        return jsonResponse(409, {
          status: 409,
          error: 'Conflict',
          message: 'Insufficient stock',
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<ProductCard product={baseProduct} />, {
      session: demoCustomerSession,
    })

    await user.click(screen.getByRole('button', { name: /add yellow bananas to cart/i }))
    expect(await screen.findByText(/insufficient stock/i)).toBeInTheDocument()
  })
})
