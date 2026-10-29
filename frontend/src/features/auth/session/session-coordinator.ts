import * as authApi from '../api/auth-api'
import type { AuthSession } from '../types'
import { clearPersistedAuth, refreshTokenStore, userSnapshotStore } from './token-store'

type MemorySession = {
  accessToken: string | null
  user: AuthSession['user'] | null
}

const memory: MemorySession = {
  accessToken: null,
  user: null,
}

let refreshInFlight: Promise<string | null> | null = null

export function getAccessToken(): string | null {
  return memory.accessToken
}

export function getMemoryUser(): AuthSession['user'] | null {
  return memory.user
}

export function applySession(session: AuthSession): void {
  memory.accessToken = session.accessToken
  memory.user = session.user
  refreshTokenStore.set(session.refreshToken)
  userSnapshotStore.set(session.user)
}

export function clearSession(): void {
  memory.accessToken = null
  memory.user = null
  clearPersistedAuth()
}

/**
 * Single-flight refresh. Returns the new access token, or null if refresh failed
 * (session is cleared on failure).
 */
export async function refreshAccessTokenSingleFlight(): Promise<string | null> {
  if (refreshInFlight) {
    return refreshInFlight
  }

  refreshInFlight = (async () => {
    const refreshToken = refreshTokenStore.get()
    if (!refreshToken) {
      clearSession()
      return null
    }

    try {
      const session = await authApi.refreshSession(refreshToken)
      applySession(session)
      return session.accessToken
    }
    catch {
      clearSession()
      return null
    }
  })().finally(() => {
    refreshInFlight = null
  })

  return refreshInFlight
}

export async function restoreSessionFromStorage(): Promise<AuthSession['user'] | null> {
  const refreshToken = refreshTokenStore.get()
  if (!refreshToken) {
    clearSession()
    return null
  }

  const access = await refreshAccessTokenSingleFlight()
  if (!access) {
    return null
  }
  return memory.user
}
