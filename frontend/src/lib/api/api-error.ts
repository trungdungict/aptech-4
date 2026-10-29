/** Mirrors backend `ApiError` JSON (GlobalExceptionHandler / RestAuthenticationEntryPoint). */
export type ApiErrorBody = {
  timestamp?: string
  status: number
  error: string
  message?: string
  path?: string
  details?: string[]
}

export class ApiClientError extends Error {
  readonly status: number
  readonly error: string
  readonly path?: string
  readonly details: string[]
  readonly body: unknown

  constructor(init: {
    status: number
    error: string
    message: string
    path?: string
    details?: string[]
    body?: unknown
  }) {
    super(init.message)
    this.name = 'ApiClientError'
    this.status = init.status
    this.error = init.error
    this.path = init.path
    this.details = init.details ?? []
    this.body = init.body
  }

  static fromResponse(status: number, body: unknown, fallbackMessage: string): ApiClientError {
    if (body && typeof body === 'object') {
      const record = body as Record<string, unknown>
      const message =
        typeof record.message === 'string' && record.message.length > 0
          ? record.message
          : fallbackMessage
      const error =
        typeof record.error === 'string' && record.error.length > 0
          ? record.error
          : statusText(status)
      const details = Array.isArray(record.details)
        ? record.details.filter((item): item is string => typeof item === 'string')
        : []
      const path = typeof record.path === 'string' ? record.path : undefined
      return new ApiClientError({
        status,
        error,
        message,
        path,
        details,
        body,
      })
    }

    return new ApiClientError({
      status,
      error: statusText(status),
      message: fallbackMessage,
      body,
    })
  }

  /** Safe for UI — never includes tokens or passwords. */
  get userMessage(): string {
    if (this.details.length > 0) {
      return this.details.join('; ')
    }
    return this.message
  }
}

function statusText(status: number): string {
  if (status === 401) return 'Unauthorized'
  if (status === 403) return 'Forbidden'
  if (status === 404) return 'Not Found'
  if (status === 409) return 'Conflict'
  if (status >= 500) return 'Server Error'
  if (status >= 400) return 'Bad Request'
  return 'Error'
}
