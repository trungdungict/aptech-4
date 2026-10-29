/**
 * Mirrors backend {@code OrderStatusTransitions} ADMIN map (ADR-008).
 * UX only — the server remains authoritative; invalid transitions return 409.
 */
const ADMIN_TRANSITIONS: Record<string, readonly string[]> = {
  PENDING_PAYMENT: ['CANCELLED'],
  PAID: ['PROCESSING', 'CANCELLED'],
  PROCESSING: ['SHIPPED', 'CANCELLED'],
  SHIPPED: ['COMPLETED'],
  COMPLETED: [],
  CANCELLED: [],
  FAILED: [],
}

export const ORDER_STATUS_VALUES = [
  'PENDING_PAYMENT',
  'PAID',
  'PROCESSING',
  'SHIPPED',
  'COMPLETED',
  'CANCELLED',
  'FAILED',
] as const

export type OrderStatusValue = (typeof ORDER_STATUS_VALUES)[number]

export function allowedAdminTransitions(from: string): readonly string[] {
  return ADMIN_TRANSITIONS[from] ?? []
}

export function isKnownOrderStatus(status: string): status is OrderStatusValue {
  return (ORDER_STATUS_VALUES as readonly string[]).includes(status)
}
