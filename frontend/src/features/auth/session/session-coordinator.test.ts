import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import * as authApi from '../api/auth-api'
import {
  applySession,
  clearSession,
  refreshAccessTokenSingleFlight,
} from './session-coordinator'
import { refreshTokenStore } from './token-store'

vi.mock('../api/auth-api', () => ({
  refreshSession: vi.fn(),
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
}))

describe('session coordinator', () => {
  beforeEach(() => {
    clearSession()
    vi.clearAllMocks()
  })

  afterEach(() => {
    clearSession()
  })

  it('clears session when refresh fails', async () => {
    refreshTokenStore.set('stale-refresh')
    vi.mocked(authApi.refreshSession).mockRejectedValue(new Error('Invalid refresh token'))

    await expect(refreshAccessTokenSingleFlight()).resolves.toBeNull()
    expect(refreshTokenStore.get()).toBeNull()
  })

  it('dedupes concurrent refresh calls', async () => {
    applySession({
      accessToken: 'a1',
      refreshToken: 'r1',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: { id: 'u1', email: 'a@b.co', fullName: 'A', roles: ['CUSTOMER'] },
    })

    let resolveRefresh: ((value: Awaited<ReturnType<typeof authApi.refreshSession>>) => void) | undefined
    vi.mocked(authApi.refreshSession).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveRefresh = resolve
        }),
    )

    const p1 = refreshAccessTokenSingleFlight()
    const p2 = refreshAccessTokenSingleFlight()

    resolveRefresh?.({
      accessToken: 'a2',
      refreshToken: 'r2',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: { id: 'u1', email: 'a@b.co', fullName: 'A', roles: ['CUSTOMER'] },
    })

    await expect(Promise.all([p1, p2])).resolves.toEqual(['a2', 'a2'])
    expect(authApi.refreshSession).toHaveBeenCalledTimes(1)
    expect(refreshTokenStore.get()).toBe('r2')
  })
})
