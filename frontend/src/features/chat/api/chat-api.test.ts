import { beforeEach, describe, expect, it, vi } from 'vitest'

import { streamChatMessage } from './chat-api'

const getAccessToken = vi.fn()
const refreshAccessTokenSingleFlight = vi.fn()

vi.mock('@/features/auth/session/session-coordinator', () => ({
  getAccessToken: () => getAccessToken(),
  refreshAccessTokenSingleFlight: () => refreshAccessTokenSingleFlight(),
}))

vi.mock('@/lib/env', () => ({
  apiBaseUrl: 'http://localhost:8080/api/v1',
}))

describe('streamChatMessage auth', () => {
  beforeEach(() => {
    getAccessToken.mockReset()
    refreshAccessTokenSingleFlight.mockReset()
    vi.unstubAllGlobals()
  })

  it('reports sign-in required when no access token and refresh fails', async () => {
    getAccessToken.mockReturnValue(null)
    refreshAccessTokenSingleFlight.mockResolvedValue(null)
    const onError = vi.fn()
    const onDone = vi.fn()

    await streamChatMessage(
      { message: 'How many orders have I placed?', language: 'EN' },
      { onDelta: vi.fn(), onDone, onError },
    )

    expect(onError).toHaveBeenCalledWith('Sign in required')
    expect(onDone).not.toHaveBeenCalled()
  })

  it('does not fall back to REST when SSE returns 401', async () => {
    getAccessToken.mockReturnValue('access-token')
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      body: null,
      json: async () => ({ error: 'Unauthorized' }),
    })
    vi.stubGlobal('fetch', fetchMock)
    const onError = vi.fn()
    const onDone = vi.fn()

    await streamChatMessage(
      { message: 'How many orders have I placed?', language: 'EN' },
      { onDelta: vi.fn(), onDone, onError },
    )

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(String((init.headers as Record<string, string>).Authorization)).toMatch(/^Bearer /)
    expect(onError).toHaveBeenCalledWith('Sign in required')
    expect(onDone).not.toHaveBeenCalled()
  })

  it('refreshes then attaches bearer token after session restore', async () => {
    getAccessToken.mockReturnValueOnce(null).mockReturnValue('restored-access')
    refreshAccessTokenSingleFlight.mockResolvedValue('restored-access')
    const encoder = new TextEncoder()
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(
          encoder.encode(
            'event:done\ndata:{"conversationId":"c1","reply":"You have placed 2 orders.","language":"EN","suggestedPrompts":[]}\n\n',
          ),
        )
        controller.close()
      },
    })
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      body,
    })
    vi.stubGlobal('fetch', fetchMock)
    const onDone = vi.fn()

    await streamChatMessage(
      { message: 'How many orders have I placed?', language: 'EN' },
      { onDelta: vi.fn(), onDone, onError: vi.fn() },
    )

    expect(refreshAccessTokenSingleFlight).toHaveBeenCalled()
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer restored-access')
    expect(onDone).toHaveBeenCalledWith(
      expect.objectContaining({ reply: 'You have placed 2 orders.' }),
    )
  })
})
