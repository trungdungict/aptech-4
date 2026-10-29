import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'

import { notificationQueryKeys } from '../hooks/notification-query-keys'
import type { AppNotification, NotificationPage } from '../types'
import { NOTIFICATION_DEFAULT_PAGE_SIZE } from '../types'

import { applyNotificationPush } from './apply-notification-push'

const userId = '11111111-1111-1111-1111-111111111111'

function sample(id: string, unread = true): AppNotification {
  return {
    id,
    type: 'ORDER_PAID',
    title: 'Order placed',
    message: 'Paid',
    orderId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    createdAt: '2026-10-29T07:00:00Z',
    readAt: unread ? null : '2026-10-29T07:01:00Z',
    unread,
  }
}

describe('applyNotificationPush', () => {
  it('prepends new items and increments unread without duplicates', () => {
    const client = new QueryClient()
    const listKey = notificationQueryKeys.list(userId, 0, NOTIFICATION_DEFAULT_PAGE_SIZE)
    client.setQueryData<NotificationPage>(listKey, {
      items: [sample('1')],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    })
    client.setQueryData(notificationQueryKeys.unreadCount(userId), { unreadCount: 1 })

    applyNotificationPush(client, userId, sample('2'))
    applyNotificationPush(client, userId, sample('2'))

    const page = client.getQueryData<NotificationPage>(listKey)
    expect(page?.items.map((i) => i.id)).toEqual(['2', '1'])
    expect(client.getQueryData(notificationQueryKeys.unreadCount(userId))).toEqual({
      unreadCount: 2,
    })
  })
})
