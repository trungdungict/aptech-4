/** Matches backend `AuthResponse` / `AuthResponse.UserResponse`. */
export type AuthUser = {
  id: string
  email: string
  fullName: string
  roles: string[]
}

export type AuthSession = {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}

export type LoginPayload = {
  email: string
  password: string
}

export type RegisterPayload = {
  email: string
  password: string
  fullName: string
}

export type AuthStatus = 'booting' | 'anonymous' | 'authenticated'
