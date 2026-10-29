import { useContext } from 'react'

import {
  NotificationRealtimeContext,
  type NotificationRealtimeValue,
} from '../context/notification-realtime-context'

export function useNotificationRealtimeContext(): NotificationRealtimeValue {
  return useContext(NotificationRealtimeContext)
}
