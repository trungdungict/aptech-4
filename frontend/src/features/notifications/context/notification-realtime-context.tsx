import { createContext, type ReactNode } from 'react'

import { useNotificationRealtime } from '../hooks/useNotificationRealtime'

export type NotificationRealtimeValue = {
  connected: boolean
}

export const NotificationRealtimeContext = createContext<NotificationRealtimeValue>({
  connected: false,
})

export function NotificationRealtimeProvider({ children }: { children: ReactNode }) {
  const value = useNotificationRealtime()
  return (
    <NotificationRealtimeContext.Provider value={value}>
      {children}
    </NotificationRealtimeContext.Provider>
  )
}
