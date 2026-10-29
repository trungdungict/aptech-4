import { apiBaseUrl } from '@/lib/env'

import { ApiClientError } from './api-error'

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export type ApiRequestOptions = {
  method?: HttpMethod
  body?: unknown
  /** When true, send Authorization: Bearer <accessToken> if available. */
  auth?: boolean
  /** Skip the single automatic refresh+retry on 401 (used by auth endpoints). */
  skipAuthRefresh?: boolean
  signal?: AbortSignal
  headers?: Record<string, string>
}

type TokenAccessor = {
  getAccessToken: () => string | null
  /** Called once on 401 for authenticated requests; must return a new access token or null. */
  refreshAccessToken: () => Promise<string | null>
}

let tokenAccessor: TokenAccessor = {
  getAccessToken: () => null,
  refreshAccessToken: async () => null,
}

/** Wire session tokens into the shared client (called from AuthProvider). */
export function configureApiClientAuth(accessor: TokenAccessor): void {
  tokenAccessor = accessor
}

export function joinApiUrl(path: string, baseUrl: string = apiBaseUrl): string {
  const base = baseUrl.replace(/\/+$/, '')
  const suffix = path.replace(/^\/+/, '')
  return `${base}/${suffix}`
}

async function parseBody(response: Response): Promise<unknown> {
  if (response.status === 204 || response.status === 205) {
    return undefined
  }

  const contentType = response.headers.get('content-type') ?? ''
  const text = await response.text()
  if (!text) {
    return undefined
  }

  if (contentType.includes('application/json')) {
    try {
      return JSON.parse(text) as unknown
    }
    catch {
      throw new ApiClientError({
        status: response.status,
        error: 'Bad Response',
        message: 'Response claimed JSON but could not be parsed',
      })
    }
  }

  return text
}

async function executeFetch(
  path: string,
  options: ApiRequestOptions,
  accessToken: string | null,
): Promise<Response> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...options.headers,
  }

  let body: BodyInit | undefined
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }

  if (options.auth && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  return fetch(joinApiUrl(path), {
    method: options.method ?? (options.body !== undefined ? 'POST' : 'GET'),
    headers,
    body,
    signal: options.signal,
  })
}

/**
 * Shared fetch wrapper for `/api/v1` resources.
 * Does not log request/response bodies (may contain credentials or tokens).
 */
export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const wantsAuth = options.auth === true
  let accessToken = wantsAuth ? tokenAccessor.getAccessToken() : null

  let response = await executeFetch(path, options, accessToken)
  let parsed = await parseBody(response)

  if (
    response.status === 401 &&
    wantsAuth &&
    !options.skipAuthRefresh
  ) {
    const refreshed = await tokenAccessor.refreshAccessToken()
    if (refreshed) {
      accessToken = refreshed
      response = await executeFetch(path, options, accessToken)
      parsed = await parseBody(response)
    }
  }

  if (!response.ok) {
    throw ApiClientError.fromResponse(
      response.status,
      parsed,
      `Request failed with status ${response.status}`,
    )
  }

  return parsed as T
}
