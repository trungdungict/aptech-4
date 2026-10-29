import { useQueryClient } from '@tanstack/react-query'
import { useEffect, type ReactNode } from 'react'

import { addressQueryKeys } from '@/features/checkout/hooks/address-query-keys'
import { cartQueryKeys } from '@/features/cart/hooks/cart-query-keys'
import { NotificationRealtimeProvider } from '@/features/notifications/context/notification-realtime-context'
import { notificationQueryKeys } from '@/features/notifications/hooks/notification-query-keys'
import { orderQueryKeys } from '@/features/orders/hooks/order-query-keys'

import { useAuth } from '../hooks/useAuth'

/**
 * Clears private cart/order/address/notification query caches on logout or user switch
 * so one session cannot leak into another. Also hosts the STOMP notification bridge.
 */
export function AuthQueryBridge({ children }: { children: ReactNode }) {
  const { status, user } = useAuth()
  const queryClient = useQueryClient()

  useEffect(() => {
    if (status === 'anonymous') {
      void queryClient.removeQueries({ queryKey: cartQueryKeys.all })
      void queryClient.removeQueries({ queryKey: orderQueryKeys.all })
      void queryClient.removeQueries({ queryKey: addressQueryKeys.all })
      void queryClient.removeQueries({ queryKey: notificationQueryKeys.all })
      void queryClient.removeQueries({ queryKey: ['admin'] })
      return
    }

    if (status === 'authenticated' && user?.id) {
      // Drop any private data that does not belong to the current subject.
      void queryClient.removeQueries({
        predicate: (query) => {
          const key = query.queryKey
          if (!Array.isArray(key) || key.length < 2) {
            return false
          }
          const scope = key[0]
          if (
            scope !== 'cart' &&
            scope !== 'orders' &&
            scope !== 'addresses' &&
            scope !== 'notifications'
          ) {
            return false
          }
          const scopedUserId = key[1]
          return typeof scopedUserId === 'string' && scopedUserId !== user.id
        },
      })
    }
  }, [status, user?.id, queryClient])

  return <NotificationRealtimeProvider>{children}</NotificationRealtimeProvider>
}
