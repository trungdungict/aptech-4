import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, type RenderOptions } from '@testing-library/react'
import type { ReactElement, ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'

import { AuthQueryBridge } from '@/features/auth/components/AuthQueryBridge'
import { AuthProvider } from '@/features/auth/context/AuthProvider'
import { applySession, clearSession } from '@/features/auth/session/session-coordinator'
import type { AuthSession } from '@/features/auth/types'

type Options = {
  route?: string
  /** When set, seeds tokens + user before AuthProvider boots (sessionStorage restore). */
  session?: AuthSession
} & Omit<RenderOptions, 'wrapper'>

export function renderWithProviders(ui: ReactElement, options: Options = {}) {
  const { route = '/', session, ...renderOptions } = options
  clearSession()
  if (session) {
    applySession(session)
  }

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[route]}>
          <AuthProvider>
            <AuthQueryBridge>{children}</AuthQueryBridge>
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>
    )
  }

  return { ...render(ui, { wrapper: Wrapper, ...renderOptions }), queryClient }
}

export const demoCustomerSession: AuthSession = {
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: {
    id: '11111111-1111-1111-1111-111111111111',
    email: 'customer@example.com',
    fullName: 'Demo Customer',
    roles: ['CUSTOMER'],
  },
}

/** Matches backend RoleCode.ADMIN in AuthResponse.user.roles — not a forged JWT. */
export const demoAdminSession: AuthSession = {
  accessToken: 'admin-access-token',
  refreshToken: 'admin-refresh-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: {
    id: '99999999-9999-9999-9999-999999999999',
    email: 'admin@example.com',
    fullName: 'Demo Admin',
    roles: ['ADMIN'],
  },
}

export const demoUnknownRoleSession: AuthSession = {
  accessToken: 'unknown-access-token',
  refreshToken: 'unknown-refresh-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: {
    id: '88888888-8888-8888-8888-888888888888',
    email: 'unknown@example.com',
    fullName: 'Unknown Role User',
    roles: [],
  },
}
