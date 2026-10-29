import { apiBaseUrl } from '@/lib/env'
import { ApiClientError } from '@/lib/api/api-error'
import { joinApiUrl, apiRequest } from '@/lib/api/api-client'
import { getAccessToken, refreshAccessTokenSingleFlight } from '@/features/auth/session/session-coordinator'

import { SseEventParser } from '../lib/sse-parser'
import type { ChatHealth, ChatLanguage, ChatResponse } from '../types'

export async function getChatHealth(signal?: AbortSignal): Promise<ChatHealth> {
  return apiRequest<ChatHealth>('/chat/health', { auth: true, signal })
}

export async function sendChatMessage(input: {
  message: string
  conversationId?: string | null
  language: ChatLanguage
  signal?: AbortSignal
}): Promise<ChatResponse> {
  return apiRequest<ChatResponse>('/chat/messages', {
    method: 'POST',
    auth: true,
    signal: input.signal,
    body: {
      message: input.message,
      conversationId: input.conversationId ?? undefined,
      language: input.language,
    },
  })
}

export type StreamHandlers = {
  onDelta: (chunk: string) => void
  onDone: (response: ChatResponse) => void
  onError: (message: string) => void
  /** Stream ended without done/error (network reset, premature EOF). Partial text may already be shown. */
  onInterrupted?: (reason: string) => void
}

export class StreamInterruptedError extends Error {
  constructor(message = 'Stream interrupted') {
    super(message)
    this.name = 'StreamInterruptedError'
  }
}

/** SSE stream via fetch. Does not auto-retry. */
export async function streamChatMessage(
  input: {
    message: string
    conversationId?: string | null
    language: ChatLanguage
    signal?: AbortSignal
  },
  handlers: StreamHandlers,
): Promise<void> {
  let token = getAccessToken()
  if (!token) {
    token = await refreshAccessTokenSingleFlight()
  }
  if (!token) {
    handlers.onError('Sign in required')
    return
  }

  let response: Response
  try {
    response = await fetch(joinApiUrl('/chat/messages/stream', apiBaseUrl), {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify({
        message: input.message,
        conversationId: input.conversationId ?? undefined,
        language: input.language,
      }),
      signal: input.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    handlers.onInterrupted?.('Network error while starting the stream')
    return
  }

  if (!response.ok || !response.body) {
    if (response.status === 401 || response.status === 403) {
      handlers.onError('Sign in required')
      return
    }
    if (response.status === 503) {
      const body = await response.json().catch(() => null)
      handlers.onError(
        body && typeof body === 'object' && 'message' in body
          ? String((body as { message: string }).message)
          : 'AI unavailable',
      )
      return
    }
    // Non-SSE failure: one-shot non-streaming fallback (not a blind retry of a partial stream).
    try {
      const completed = await sendChatMessage(input)
      handlers.onDone(completed)
    } catch (error) {
      handlers.onError(error instanceof ApiClientError ? error.userMessage : 'Chat failed')
    }
    return
  }

  const reader = response.body.getReader()
  const parser = new SseEventParser()
  let terminal: 'done' | 'error' | null = null

  const dispatch = (events: ReturnType<SseEventParser['push']>) => {
    for (const event of events) {
      if (terminal) {
        break
      }
      if (event.type === 'delta') {
        handlers.onDelta(event.data)
      } else if (event.type === 'done') {
        terminal = 'done'
        try {
          handlers.onDone(JSON.parse(event.data) as ChatResponse)
        } catch {
          terminal = 'error'
          handlers.onError('Invalid stream completion')
        }
      } else if (event.type === 'error') {
        terminal = 'error'
        handlers.onError(event.data || 'Stream error')
      }
    }
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      if (value) {
        dispatch(parser.push(value))
      }
      if (terminal) {
        break
      }
    }
    if (!terminal) {
      dispatch(parser.finish())
    }
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    if (!terminal) {
      handlers.onInterrupted?.('Connection lost while streaming')
      return
    }
    throw error
  } finally {
    try {
      reader.releaseLock()
    } catch {
      // already released
    }
  }

  if (!terminal) {
    handlers.onInterrupted?.('Stream ended before completion')
  }
}
