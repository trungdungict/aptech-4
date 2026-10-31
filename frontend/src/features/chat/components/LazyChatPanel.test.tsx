import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import {
  demoAdminSession,
  demoCustomerSession,
  renderWithProviders,
} from '@/test/render-app'

import { ChatPanelFallback } from './ChatPanelFallback'
import { LazyChatPanel } from './LazyChatPanel'

vi.mock('../api/chat-api', () => ({
  getChatHealth: vi.fn(async () => ({
    enabled: false,
    modelConfigured: false,
    model: 'llama3.2',
    status: 'DISABLED',
    detail: 'off',
  })),
  streamChatMessage: vi.fn(),
  sendChatMessage: vi.fn(),
}))

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('LazyChatPanel', () => {
  beforeEach(() => {
    clearSession()
    localStorage.clear()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('exposes a lightweight loading fallback without starting SSE', () => {
    renderWithProviders(<ChatPanelFallback />)
    expect(screen.getByRole('button', { name: /loading chat/i })).toBeDisabled()
    expect(screen.queryByTestId('chat-composer')).not.toBeInTheDocument()
  })

  it('lazy-loads the customer chat launcher for authenticated users', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/', session: demoCustomerSession })

    expect(
      await screen.findByRole('button', { name: /Aptech Assistant/i }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /notifications/i })).toBeInTheDocument()
  })

  it('does not mount chat for anonymous visitors', async () => {
    vi.mocked(fetch).mockImplementation(async () =>
      jsonResponse(401, { status: 401, error: 'Unauthorized', message: 'no session' }),
    )

    renderWithProviders(<AppRouter />, { route: '/' })

    await waitFor(() => {
      expect(screen.getAllByRole('link', { name: /sign in/i }).length).toBeGreaterThan(0)
    })
    expect(screen.queryByRole('button', { name: /Aptech Assistant/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /loading chat/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /notifications/i })).not.toBeInTheDocument()
  })

  it('preserves admin variant after lazy load', async () => {
    const user = userEvent.setup()
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoAdminSession)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, { route: '/admin', session: demoAdminSession })

    const launcher = await screen.findByRole('button', { name: /Aptech Assistant/i })
    await user.click(launcher)
    expect(screen.getByTestId('chat-composer')).toBeInTheDocument()
  })

  it('remounts a fresh panel when the user id key changes', async () => {
    const first = renderWithProviders(<LazyChatPanel key="user-a" variant="customer" />)
    expect(
      await screen.findByRole('button', { name: /Aptech Assistant/i }),
    ).toBeInTheDocument()
    first.unmount()

    renderWithProviders(<LazyChatPanel key="user-b" variant="customer" />)
    // Fresh mount after key change — launcher present, panel closed (no SSE).
    expect(
      await screen.findByRole('button', { name: /Aptech Assistant/i }),
    ).toBeInTheDocument()
    expect(screen.queryByTestId('chat-composer')).not.toBeInTheDocument()
  })

  it('aborts chat work on unmount after the panel was opened', async () => {
    const user = userEvent.setup()
    const { streamChatMessage } = await import('../api/chat-api')
    const seen = { signal: undefined as AbortSignal | undefined }
    vi.mocked(streamChatMessage).mockImplementation(async (input) => {
      seen.signal = input.signal
      await new Promise(() => {
        // never resolves — unmount should abort
      })
    })

    const { unmount } = renderWithProviders(<LazyChatPanel variant="customer" />)
    await user.click(await screen.findByRole('button', { name: /Aptech Assistant/i }))
    await user.type(screen.getByRole('textbox', { name: /Ask a question/i }), 'hello')
    await user.click(screen.getByRole('button', { name: /^Send$/i }))

    await waitFor(() => {
      expect(seen.signal).toBeDefined()
    })

    unmount()
    expect(seen.signal?.aborted).toBe(true)
  })
})
