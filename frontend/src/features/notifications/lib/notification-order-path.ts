import { hasAdminRole } from '@/features/auth/lib/roles'

/** Customer storefront vs admin workspace order detail route. */
export function notificationOrderPath(
  orderId: string,
  roles: readonly string[] | null | undefined,
): string {
  if (hasAdminRole(roles)) {
    return `/admin/orders/${orderId}`
  }
  return `/orders/${orderId}`
}
