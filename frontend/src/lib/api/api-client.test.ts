import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiClientError } from './api-error'
import { apiRequest, configureApiClientAuth, joinApiUrl } from './api-client'

describe('api client', () => {
  beforeEach(() => {
    configureApiClientAuth({
      getAccessToken: () => null,
      refreshAccessToken: async () => null,
      invalidateSession: () => undefined,
    })
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('joins base URL and path', () => {
    expect(joinApiUrl('/auth/login', 'http://localhost:8080/api/v1')).toBe(
      'http://localhost:8080/api/v1/auth/login',
    )
    expect(joinApiUrl('/auth/login', '/api/v1')).toBe('/api/v1/auth/login')
  })

  it('parses successful JSON responses', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(apiRequest<{ ok: boolean }>('/health')).resolves.toEqual({ ok: true })
  })

  it('returns undefined for 204 No Content', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }))

    await expect(apiRequest<undefined>('/auth/logout', { method: 'POST', body: {} })).resolves.toBeUndefined()
  })

  it('throws typed ApiClientError for non-success JSON bodies', async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 401,
          error: 'Unauthorized',
          message: 'Invalid email or password',
          path: '/api/v1/auth/login',
          details: [],
        }),
        { status: 401, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const error = await apiRequest('/auth/login', {
      method: 'POST',
      body: { email: 'a@b.co', password: 'secret' },
      skipAuthRefresh: true,
    }).catch((err: unknown) => err)

    expect(error).toBeInstanceOf(ApiClientError)
    const apiError = error as ApiClientError
    expect(apiError.status).toBe(401)
    expect(apiError.userMessage).toBe('Invalid email or password')
    expect(apiError.userMessage).not.toMatch(/secret|Bearer|eyJ/)
  })

  it('coordinates a single refresh on 401 then retries', async () => {
    const refresh = vi.fn(async () => 'new-access')
    const invalidate = vi.fn()
    configureApiClientAuth({
      getAccessToken: () => 'expired',
      refreshAccessToken: refresh,
      invalidateSession: invalidate,
    })

    vi.mocked(fetch)
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ status: 401, error: 'Unauthorized' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ items: [] }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      )

    await expect(apiRequest<{ items: unknown[] }>('/cart', { auth: true })).resolves.toEqual({
      items: [],
    })
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(invalidate).not.toHaveBeenCalled()
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2)
    const secondHeaders = vi.mocked(fetch).mock.calls[1]?.[1] as RequestInit
    expect(secondHeaders.headers).toMatchObject({ Authorization: 'Bearer new-access' })
  })

  it('invalidates the session when refresh fails on an authenticated 401', async () => {
    const invalidate = vi.fn()
    configureApiClientAuth({
      getAccessToken: () => 'expired',
      refreshAccessToken: async () => null,
      invalidateSession: invalidate,
    })

    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ status: 401, error: 'Unauthorized', message: 'expired' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(apiRequest('/admin/orders', { auth: true })).rejects.toMatchObject({ status: 401 })
    expect(invalidate).toHaveBeenCalledTimes(1)
  })

  it('invalidates the session when refresh succeeds but the retry is still 401', async () => {
    const invalidate = vi.fn()
    configureApiClientAuth({
      getAccessToken: () => 'expired',
      refreshAccessToken: async () => 'still-bad',
      invalidateSession: invalidate,
    })

    vi.mocked(fetch).mockImplementation(async () =>
      new Response(JSON.stringify({ status: 401, error: 'Unauthorized' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(apiRequest('/admin/orders', { auth: true })).rejects.toMatchObject({ status: 401 })
    expect(invalidate).toHaveBeenCalledTimes(1)
    expect(vi.mocked(fetch)).toHaveBeenCalledTimes(2)
  })

  it('does not invalidate the session on 403', async () => {
    const invalidate = vi.fn()
    const refresh = vi.fn(async () => 'new-access')
    configureApiClientAuth({
      getAccessToken: () => 'access',
      refreshAccessToken: refresh,
      invalidateSession: invalidate,
    })

    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ status: 403, error: 'Forbidden', message: 'Forbidden' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(apiRequest('/admin/orders', { auth: true })).rejects.toMatchObject({ status: 403 })
    expect(refresh).not.toHaveBeenCalled()
    expect(invalidate).not.toHaveBeenCalled()
  })

  it('does not invalidate the session on 5xx / network-style failures', async () => {
    const invalidate = vi.fn()
    configureApiClientAuth({
      getAccessToken: () => 'access',
      refreshAccessToken: async () => 'new-access',
      invalidateSession: invalidate,
    })

    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ status: 503, error: 'Unavailable', message: 'down' }), {
        status: 503,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    await expect(apiRequest('/admin/orders', { auth: true })).rejects.toMatchObject({ status: 503 })
    expect(invalidate).not.toHaveBeenCalled()
  })

})
