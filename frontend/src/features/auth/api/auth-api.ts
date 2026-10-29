import { apiRequest } from '@/lib/api/api-client'

import type { AuthSession, LoginPayload, RegisterPayload } from '../types'

type AuthResponseDto = {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: {
    id: string
    email: string
    fullName: string
    roles: string[]
  }
}

function toSession(dto: AuthResponseDto): AuthSession {
  return {
    accessToken: dto.accessToken,
    refreshToken: dto.refreshToken,
    tokenType: dto.tokenType,
    expiresIn: dto.expiresIn,
    user: {
      id: String(dto.user.id),
      email: dto.user.email,
      fullName: dto.user.fullName,
      roles: [...dto.user.roles],
    },
  }
}

/** POST /api/v1/auth/register → 201 AuthResponse (session issued; role always CUSTOMER). */
export async function register(payload: RegisterPayload): Promise<AuthSession> {
  const dto = await apiRequest<AuthResponseDto>('/auth/register', {
    method: 'POST',
    body: {
      email: payload.email,
      password: payload.password,
      fullName: payload.fullName,
    },
    skipAuthRefresh: true,
  })
  return toSession(dto)
}

/** POST /api/v1/auth/login → 200 AuthResponse */
export async function login(payload: LoginPayload): Promise<AuthSession> {
  const dto = await apiRequest<AuthResponseDto>('/auth/login', {
    method: 'POST',
    body: {
      email: payload.email,
      password: payload.password,
    },
    skipAuthRefresh: true,
  })
  return toSession(dto)
}

/** POST /api/v1/auth/refresh → 200 AuthResponse (rotated refresh token). */
export async function refreshSession(refreshToken: string): Promise<AuthSession> {
  const dto = await apiRequest<AuthResponseDto>('/auth/refresh', {
    method: 'POST',
    body: { refreshToken },
    skipAuthRefresh: true,
  })
  return toSession(dto)
}

/**
 * POST /api/v1/auth/logout → 204.
 * Revokes the presented refresh token server-side (idempotent).
 */
export async function logout(refreshToken: string): Promise<void> {
  await apiRequest<undefined>('/auth/logout', {
    method: 'POST',
    body: { refreshToken },
    skipAuthRefresh: true,
  })
}
