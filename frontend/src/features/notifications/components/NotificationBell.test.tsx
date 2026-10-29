import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Route, Routes } from 'react-router-dom'

import {
  demoAdminSession,
  demoCustomerSession,
  renderWithProviders,
} from '@/test/render-app'

import { NotificationBell } from './NotificationBell'

const orderId = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
const notifId = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function mockFetch(
  session: typeof demoCustomerSession | typeof demoAdminSession,
  handler: (url: string) => Response | Promise<Response>,
) {
  vi.mocked(fetch).mockImplementation(async (input) => {
    const url = String(input)
    if (url.includes('/auth/refresh')) {
      return jsonResponse(session)
    }
    return handler(url)
  })
}

describe('NotificationBell', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows unread badge, list states, mark-read, and navigates to order detail', async () => {
    const user = userEvent.setup()
    mockFetch(demoCustomerSession, (url) => {
      if (url.includes('/notifications/unread-count')) {
        return jsonResponse({ unreadCount: 2 })
      }
      if (url.includes('/notifications/read-all')) {
        return jsonResponse({ unreadCount: 0 })
      }
      if (url.includes(`/notifications/${notifId}/read`)) {
        return jsonResponse({
          id: notifId,
          type: 'ORDER_PAID',
          title: 'Order placed',
          message: 'Paid',
          orderId,
          createdAt: '2026-10-29T07:00:00Z',
          readAt: '2026-10-29T07:01:00Z',
          unread: false,
        })
      }
      if (url.includes('/notifications')) {
        return jsonResponse({
          items: [
            {
              id: notifId,
              type: 'ORDER_PAID',
              title: 'Order placed',
              message: 'Your order was paid.',
              orderId,
              createdAt: '2026-10-29T07:00:00Z',
              readAt: null,
              unread: true,
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        })
      }
      return jsonResponse({ error: 'Not Found', message: 'missing' }, 404)
    })

    renderWithProviders(
      <Routes>
        <Route path="/" element={<NotificationBell />} />
        <Route path="/orders/:orderId" element={<p>Customer order page</p>} />
      </Routes>,
      { session: demoCustomerSession, route: '/' },
    )

    const bell = await screen.findByRole('button', { name: /Notifications, 2 unread/i })
    expect(within(bell).getByText('2')).toBeInTheDocument()

    await user.click(bell)
    expect(await screen.findByText('Order placed')).toBeInTheDocument()
    expect(screen.getByText('Your order was paid.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Order placed/i }))
    await waitFor(() => {
      expect(screen.getByText('Customer order page')).toBeInTheDocument()
    })
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(
      expect.stringContaining(`/notifications/${notifId}/read`),
      expect.objectContaining({ method: 'PATCH' }),
    )
  })

  it('shows empty and error/retry states', async () => {
    const user = userEvent.setup()
    let failList = true

    mockFetch(demoCustomerSession, (url) => {
      if (url.includes('/notifications/unread-count')) {
        return jsonResponse({ unreadCount: 0 })
      }
      if (url.includes('/notifications')) {
        if (failList) {
          return jsonResponse({ error: 'Error', message: 'boom' }, 500)
        }
        return jsonResponse({
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        })
      }
      return jsonResponse({}, 404)
    })

    renderWithProviders(<NotificationBell />, { session: demoCustomerSession })

    await user.click(await screen.findByRole('button', { name: /^Notifications$/i }))
    expect(await screen.findByRole('button', { name: /Retry/i })).toBeInTheDocument()

    failList = false
    await user.click(screen.getByRole('button', { name: /Retry/i }))
    expect(await screen.findByText('No notifications yet.')).toBeInTheDocument()
  })

  it('marks all as read and routes admin clicks to admin order detail', async () => {
    const user = userEvent.setup()
    mockFetch(demoAdminSession, (url) => {
      if (url.includes('/notifications/unread-count')) {
        return jsonResponse({ unreadCount: 1 })
      }
      if (url.includes('/notifications/read-all')) {
        return jsonResponse({ unreadCount: 0 })
      }
      if (url.includes('/notifications')) {
        return jsonResponse({
          items: [
            {
              id: notifId,
              type: 'ORDER_PAID',
              title: 'New paid order',
              message: 'Needs fulfillment',
              orderId,
              createdAt: '2026-10-29T07:00:00Z',
              readAt: null,
              unread: true,
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        })
      }
      return jsonResponse({}, 404)
    })

    renderWithProviders(
      <Routes>
        <Route path="/" element={<NotificationBell />} />
        <Route path="/admin/orders/:orderId" element={<p>Admin order page</p>} />
      </Routes>,
      { session: demoAdminSession, route: '/' },
    )

    await user.click(await screen.findByRole('button', { name: /Notifications, 1 unread/i }))
    await user.click(screen.getByRole('button', { name: /Mark all as read/i }))
    await waitFor(() => {
      expect(vi.mocked(fetch)).toHaveBeenCalledWith(
        expect.stringContaining('/notifications/read-all'),
        expect.objectContaining({ method: 'PATCH' }),
      )
    })

    await user.click(screen.getByRole('button', { name: /New paid order/i }))
    await waitFor(() => {
      expect(screen.getByText('Admin order page')).toBeInTheDocument()
    })
  })

  it('does not render when anonymous', () => {
    renderWithProviders(<NotificationBell />)
    expect(screen.queryByRole('button', { name: /Notifications/i })).not.toBeInTheDocument()
  })
})
