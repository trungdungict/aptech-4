/**
 * Role helpers aligned with backend {@code RoleCode} values returned in
 * {@code AuthResponse.user.roles} (login / register / refresh).
 *
 * Frontend gating is UX only — Spring Security still enforces `/api/v1/admin/**`.
 * Do not decode JWTs or invent roles from email/URL/localStorage.
 */

export const ROLE_ADMIN = 'ADMIN'
export const ROLE_CUSTOMER = 'CUSTOMER'

/** True only when the trusted auth payload includes the ADMIN role code. */
export function hasAdminRole(roles: readonly string[] | null | undefined): boolean {
  if (!roles || roles.length === 0) {
    return false
  }
  return roles.includes(ROLE_ADMIN)
}
