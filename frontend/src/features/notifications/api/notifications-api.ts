import { apiRequest } from '@/lib/api/api-client'
import { toQueryString } from '@/lib/api/query-string'

import type { AppNotification, NotificationPage, UnreadCount } from '../types'
import { NOTIFICATION_DEFAULT_PAGE_SIZE } from '../types'

/** GET /api/v1/notifications */
export function listNotifications(
  page = 0,
  size = NOTIFICATION_DEFAULT_PAGE_SIZE,
  signal?: AbortSignal,
): Promise<NotificationPage> {
  const query = toQueryString({ page, size })
  return apiRequest<NotificationPage>(`/notifications${query}`, {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** GET /api/v1/notifications/unread-count */
export function getUnreadCount(signal?: AbortSignal): Promise<UnreadCount> {
  return apiRequest<UnreadCount>('/notifications/unread-count', {
    method: 'GET',
    auth: true,
    signal,
  })
}

/** PATCH /api/v1/notifications/{id}/read */
export function markNotificationRead(
  id: string,
  signal?: AbortSignal,
): Promise<AppNotification> {
  return apiRequest<AppNotification>(`/notifications/${id}/read`, {
    method: 'PATCH',
    auth: true,
    signal,
  })
}

/** PATCH /api/v1/notifications/read-all */
export function markAllNotificationsRead(signal?: AbortSignal): Promise<UnreadCount> {
  return apiRequest<UnreadCount>('/notifications/read-all', {
    method: 'PATCH',
    auth: true,
    signal,
  })
}
