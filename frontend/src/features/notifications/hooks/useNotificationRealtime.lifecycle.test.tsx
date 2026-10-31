import { screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { demoCustomerSession, renderWithProviders } from '@/test/render-app'

import { NotificationRealtimeProvider } from '../context/notification-realtime-context'
import { useNotificationRealtimeContext } from './useNotificationRealtimeContext'

/**
 * STOMP itself is skipped when import.meta.env.MODE === 'test' (no broker).
 * These tests lock the shared-provider mounting contract so lazy ChatPanel
 * work cannot accidentally duplicate or relocate the notification bridge.
 */
function ConnectedProbe() {
  const { connected } = useNotificationRealtimeContext()
  return <span data-testid="stomp-connected">{String(connected)}</span>
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('Notification realtime lifecycle (Phase 7)', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('keeps a single shared provider value for nested consumers', () => {
    renderWithProviders(
      <NotificationRealtimeProvider>
        <ConnectedProbe />
        <ConnectedProbe />
      </NotificationRealtimeProvider>,
      { session: demoCustomerSession },
    )

    const probes = screen.getAllByTestId('stomp-connected')
    expect(probes).toHaveLength(2)
    expect(probes[0]).toHaveTextContent('false')
    expect(probes[1]).toHaveTextContent('false')
  })

  it('still mounts NotificationBell for authenticated storefront users', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      if (url.includes('/notifications/unread-count')) {
        return jsonResponse(200, { unreadCount: 0 })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/', session: demoCustomerSession })

    expect(await screen.findByRole('button', { name: /notifications/i })).toBeInTheDocument()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /notifications/i })).toHaveAttribute(
        'aria-expanded',
        'false',
      )
    })
  })

  it('does not report connected while the test-mode STOMP path is skipped', () => {
    renderWithProviders(<ConnectedProbe />, { session: demoCustomerSession })
    expect(screen.getByTestId('stomp-connected')).toHaveTextContent('false')
  })
})
