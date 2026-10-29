import type { PageResponse } from '@/features/catalog/types'

/** Matches backend `NotificationResponse`. */
export type AppNotification = {
  id: string
  type: string
  title: string
  message: string
  orderId: string | null
  createdAt: string
  readAt: string | null
  unread: boolean
}

export type NotificationPage = PageResponse<AppNotification>

export type UnreadCount = {
  unreadCount: number
}

export const NOTIFICATION_DEFAULT_PAGE_SIZE = 20

/** Private STOMP destination (must match backend NotificationDestinations.USER_QUEUE). */
export const NOTIFICATION_USER_QUEUE = '/user/queue/notifications'

/**
 * Slow REST fallback while the WebSocket is disconnected (ADR-018).
 * Primary live updates come from STOMP; this only covers temporary disconnect gaps.
 */
export const NOTIFICATION_FALLBACK_POLL_MS = 120_000
