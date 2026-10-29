/**
 * Refresh-token persistence for SPA demos (ADR-013).
 *
 * Backend returns refresh tokens in JSON (not HttpOnly cookies). Surviving a page
 * reload therefore requires some client storage. We use **sessionStorage** (tab-scoped),
 * not localStorage, behind this isolated module so the strategy can change later.
 *
 * XSS can still read sessionStorage — treat as a school-demo trade-off, not production-hardened.
 * Access tokens stay in memory only (see AuthProvider).
 */

const REFRESH_KEY = 'aptech4.auth.refreshToken'
const USER_KEY = 'aptech4.auth.user'

export type StoredAuthUser = {
  id: string
  email: string
  fullName: string
  roles: string[]
}

function storage(): Storage | null {
  if (typeof window === 'undefined') {
    return null
  }
  try {
    return window.sessionStorage
  }
  catch {
    return null
  }
}

export const refreshTokenStore = {
  get(): string | null {
    return storage()?.getItem(REFRESH_KEY) ?? null
  },
  set(token: string): void {
    storage()?.setItem(REFRESH_KEY, token)
  },
  clear(): void {
    storage()?.removeItem(REFRESH_KEY)
  },
}

export const userSnapshotStore = {
  get(): StoredAuthUser | null {
    const raw = storage()?.getItem(USER_KEY)
    if (!raw) {
      return null
    }
    try {
      const parsed = JSON.parse(raw) as StoredAuthUser
      if (
        typeof parsed.id === 'string' &&
        typeof parsed.email === 'string' &&
        typeof parsed.fullName === 'string' &&
        Array.isArray(parsed.roles)
      ) {
        return parsed
      }
      return null
    }
    catch {
      return null
    }
  },
  set(user: StoredAuthUser): void {
    storage()?.setItem(USER_KEY, JSON.stringify(user))
  },
  clear(): void {
    storage()?.removeItem(USER_KEY)
  },
}

export function clearPersistedAuth(): void {
  refreshTokenStore.clear()
  userSnapshotStore.clear()
}
