import { createContext } from 'react'

import type { AuthStatus, AuthUser, LoginPayload, RegisterPayload } from '../types'

export type AuthContextValue = {
  status: AuthStatus
  user: AuthUser | null
  login: (payload: LoginPayload) => Promise<void>
  register: (payload: RegisterPayload) => Promise<void>
  logout: () => Promise<{ serverLogoutOk: boolean }>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
