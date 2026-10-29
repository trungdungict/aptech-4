import type { QueryClient } from '@tanstack/react-query'

import { notificationQueryKeys } from '../hooks/notification-query-keys'
import type { AppNotification, NotificationPage, UnreadCount } from '../types'
import { NOTIFICATION_DEFAULT_PAGE_SIZE } from '../types'

/** Merge a pushed notification into TanStack caches without duplicates. */
export function applyNotificationPush(
  queryClient: QueryClient,
  userId: string,
  incoming: AppNotification,
): void {
  const listKey = notificationQueryKeys.list(userId, 0, NOTIFICATION_DEFAULT_PAGE_SIZE)
  const previous = queryClient.getQueryData<NotificationPage>(listKey)
  const alreadyPresent = previous?.items.some((item) => item.id === incoming.id) ?? false

  queryClient.setQueryData<NotificationPage>(listKey, (current) => {
    if (!current) {
      return {
        items: [incoming],
        page: 0,
        size: NOTIFICATION_DEFAULT_PAGE_SIZE,
        totalElements: 1,
        totalPages: 1,
        first: true,
        last: true,
      }
    }
    if (current.items.some((item) => item.id === incoming.id)) {
      return current
    }
    const items = [incoming, ...current.items].slice(0, current.size)
    return {
      ...current,
      items,
      totalElements: current.totalElements + 1,
      totalPages: Math.max(1, Math.ceil((current.totalElements + 1) / current.size)),
      last: items.length >= current.totalElements + 1,
    }
  })

  if (!alreadyPresent && incoming.unread) {
    queryClient.setQueryData<UnreadCount>(
      notificationQueryKeys.unreadCount(userId),
      (current) => ({ unreadCount: (current?.unreadCount ?? 0) + 1 }),
    )
  }
}
