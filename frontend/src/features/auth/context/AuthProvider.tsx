import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { configureApiClientAuth } from '@/lib/api/api-client'

import * as authApi from '../api/auth-api'
import {
  applySession,
  clearSession,
  getAccessToken,
  getMemoryUser,
  refreshAccessTokenSingleFlight,
  restoreSessionFromStorage,
} from '../session/session-coordinator'
import { refreshTokenStore } from '../session/token-store'
import type { AuthStatus, AuthUser, LoginPayload, RegisterPayload } from '../types'
import { AuthContext } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('booting')
  const [user, setUser] = useState<AuthUser | null>(null)

  useEffect(() => {
    configureApiClientAuth({
      getAccessToken,
      refreshAccessToken: refreshAccessTokenSingleFlight,
    })

    let cancelled = false
    void (async () => {
      try {
        const restored = await restoreSessionFromStorage()
        if (cancelled) {
          return
        }
        if (restored) {
          setUser(restored)
          setStatus('authenticated')
        }
        else {
          setUser(null)
          setStatus('anonymous')
        }
      }
      catch {
        if (!cancelled) {
          clearSession()
          setUser(null)
          setStatus('anonymous')
        }
      }
    })()

    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (payload: LoginPayload) => {
    const session = await authApi.login(payload)
    applySession(session)
    setUser(session.user)
    setStatus('authenticated')
  }, [])

  const register = useCallback(async (payload: RegisterPayload) => {
    const session = await authApi.register(payload)
    applySession(session)
    setUser(session.user)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(async () => {
    const refreshToken = refreshTokenStore.get()
    let serverLogoutOk = false
    if (refreshToken) {
      try {
        await authApi.logout(refreshToken)
        serverLogoutOk = true
      }
      catch {
        serverLogoutOk = false
      }
    }
    clearSession()
    setUser(null)
    setStatus('anonymous')
    return { serverLogoutOk }
  }, [])

  const value = useMemo(
    () => ({
      status,
      user: user ?? getMemoryUser(),
      login,
      register,
      logout,
    }),
    [status, user, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
