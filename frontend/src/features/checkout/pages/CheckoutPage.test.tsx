import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { demoCustomerSession, renderWithProviders } from '@/test/render-app'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const cartFixture = {
  id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  items: [
    {
      id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
      productId: '22222222-2222-2222-2222-222222222222',
      productName: 'Demo Smartphone X',
      imageUrl: null,
      quantity: 1,
      unitPrice: 12990000,
      lineSubtotal: 12990000,
      currency: 'VND',
      available: true,
      productActive: true,
    },
  ],
  subtotal: 12990000,
  currency: 'VND',
  hasUnavailableItems: false,
}

const addressFixture = {
  id: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
  label: 'Home',
  recipientName: 'Demo Customer',
  line1: '1 Demo Street',
  line2: null,
  city: 'Hanoi',
  stateRegion: null,
  postalCode: '10000',
  countryCode: 'VN',
  phone: null,
  defaultAddress: true,
}

const checkoutSuccess = {
  orderId: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
  orderStatus: 'PAID',
  subtotalAmount: 12990000,
  shippingAmount: 0,
  totalAmount: 12990000,
  currency: 'VND',
  placedAt: '2026-01-01T00:00:00Z',
  items: [
    {
      productId: '22222222-2222-2222-2222-222222222222',
      productSku: 'DEMO-ELEC-PHONE',
      productName: 'Demo Smartphone X',
      unitPrice: 12990000,
      quantity: 1,
      lineTotal: 12990000,
    },
  ],
  payment: {
    paymentId: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
    provider: 'MOCK',
    status: 'SUCCEEDED',
    amount: 12990000,
    currency: 'VND',
    providerReference: 'mock-ref',
    failureReason: null,
  },
  cartCleared: true,
}

const orderDetail = {
  id: checkoutSuccess.orderId,
  userId: demoCustomerSession.user.id,
  status: 'PAID',
  subtotalAmount: 12990000,
  shippingAmount: 0,
  totalAmount: 12990000,
  currency: 'VND',
  placedAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  shipping: {
    recipientName: 'Demo Customer',
    line1: '1 Demo Street',
    line2: null,
    city: 'Hanoi',
    stateRegion: null,
    postalCode: '10000',
    countryCode: 'VN',
    phone: null,
  },
  items: checkoutSuccess.items,
  payment: checkoutSuccess.payment,
}

function mockFetchRouter(
  handler: (url: string, init?: RequestInit) => Response | Promise<Response>,
) {
  vi.mocked(fetch).mockImplementation(async (input, init) => {
    const url = String(input)
    if (url.includes('/auth/refresh')) {
      return jsonResponse(200, demoCustomerSession)
    }
    return handler(url, init)
  })
}

describe('CheckoutPage', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('reviews server cart and places order with stable idempotency key', async () => {
    const user = userEvent.setup()
    const checkoutBodies: unknown[] = []

    mockFetchRouter((url, init) => {
      if (url.includes('/addresses') && (!init?.method || init.method === 'GET')) {
        return jsonResponse(200, [addressFixture])
      }
      if (url.includes('/checkout') && init?.method === 'POST') {
        checkoutBodies.push(JSON.parse(String(init.body)))
        return jsonResponse(201, checkoutSuccess)
      }
      if (url.includes(`/orders/${checkoutSuccess.orderId}`)) {
        return jsonResponse(200, orderDetail)
      }
      if (url.includes('/cart')) {
        return jsonResponse(200, cartFixture)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/checkout', session: demoCustomerSession })

    expect(await screen.findByText(/order review/i)).toBeInTheDocument()
    expect(screen.getByText(/demo smartphone x/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /place order \(mock payment\)/i })).toBeInTheDocument()

    const place = screen.getByRole('button', { name: /place order \(mock payment\)/i })
    await user.click(place)
    await user.click(place)

    await waitFor(() => {
      expect(checkoutBodies.length).toBe(1)
    })
    const body = checkoutBodies[0] as { idempotencyKey: string; addressId: string }
    expect(body.addressId).toBe(addressFixture.id)
    expect(body.idempotencyKey.length).toBeGreaterThan(8)

    expect(await screen.findByRole('heading', { name: /order detail/i })).toBeInTheDocument()
    expect(screen.getAllByText(checkoutSuccess.orderId).length).toBeGreaterThan(0)
  })

  it('handles insufficient stock conflict without marking success', async () => {
    const user = userEvent.setup()

    mockFetchRouter((url, init) => {
      if (url.includes('/addresses')) {
        return jsonResponse(200, [addressFixture])
      }
      if (url.includes('/checkout') && init?.method === 'POST') {
        return jsonResponse(409, {
          status: 409,
          error: 'Conflict',
          message: 'Insufficient stock',
          details: [],
        })
      }
      if (url.includes('/cart')) {
        return jsonResponse(200, cartFixture)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/checkout', session: demoCustomerSession })
    await user.click(await screen.findByRole('button', { name: /place order/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/insufficient stock/i)
    expect(screen.queryByText(/order detail/i)).not.toBeInTheDocument()
  })

  it('treats network failure as unknown outcome and reuses the same key on retry', async () => {
    const user = userEvent.setup()
    const keys: string[] = []
    let calls = 0

    mockFetchRouter((url, init) => {
      if (url.includes('/addresses')) {
        return jsonResponse(200, [addressFixture])
      }
      if (url.includes('/checkout') && init?.method === 'POST') {
        calls += 1
        const body = JSON.parse(String(init.body)) as { idempotencyKey: string }
        keys.push(body.idempotencyKey)
        if (calls === 1) {
          return Promise.reject(new TypeError('network down'))
        }
        return jsonResponse(201, checkoutSuccess)
      }
      if (url.includes(`/orders/${checkoutSuccess.orderId}`)) {
        return jsonResponse(200, orderDetail)
      }
      if (url.includes('/cart')) {
        return jsonResponse(200, cartFixture)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/checkout', session: demoCustomerSession })
    await user.click(await screen.findByRole('button', { name: /place order/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/outcome is unknown/i)
    await user.click(screen.getByRole('button', { name: /retry same attempt/i }))

    await waitFor(() => {
      expect(keys.length).toBe(2)
    })
    expect(keys[0]).toBe(keys[1])
    expect(await screen.findByText(/order detail/i)).toBeInTheDocument()
  })
})
