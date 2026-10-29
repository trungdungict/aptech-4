import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'

import { useAuth } from '@/features/auth/hooks/useAuth'
import {
  getAccessToken,
  refreshAccessTokenSingleFlight,
} from '@/features/auth/session/session-coordinator'

import { notificationQueryKeys } from './notification-query-keys'
import { applyNotificationPush } from '../lib/apply-notification-push'
import { wsBrokerUrl } from '../lib/ws-broker-url'
import type { AppNotification } from '../types'
import { NOTIFICATION_USER_QUEUE } from '../types'

const MAX_RECONNECT_ATTEMPTS = 8
const BASE_RECONNECT_MS = 1_500

/**
 * Authenticated STOMP lifecycle for private notification push (ADR-018).
 * Connects only while authenticated; disconnects on logout / user switch.
 */
export function useNotificationRealtime(): { connected: boolean } {
  const { status, user } = useAuth()
  const userId = user?.id
  const queryClient = useQueryClient()
  const [connected, setConnected] = useState(false)
  const clientRef = useRef<Client | null>(null)
  const subscriptionRef = useRef<StompSubscription | null>(null)
  const userIdRef = useRef(userId)

  useEffect(() => {
    userIdRef.current = userId
  }, [userId])

  useEffect(() => {
    if (status !== 'authenticated' || !userId) {
      tearDown(clientRef, subscriptionRef, setConnected)
      return
    }

    // Vitest has no real broker; REST + unit tests cover cache merge / URL helpers.
    if (import.meta.env.MODE === 'test') {
      return
    }

    let cancelled = false
    let reconnectAttempts = 0

    const client = new Client({
      brokerURL: wsBrokerUrl(),
      reconnectDelay: 0, // we control reconnect below
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      beforeConnect: async () => {
        let token = getAccessToken()
        if (!token) {
          token = await refreshAccessTokenSingleFlight()
        }
        if (!token) {
          throw new Error('No access token for STOMP CONNECT')
        }
        client.connectHeaders = {
          Authorization: `Bearer ${token}`,
        }
      },
      onConnect: () => {
        if (cancelled) {
          return
        }
        reconnectAttempts = 0
        setConnected(true)
        subscriptionRef.current?.unsubscribe()
        subscriptionRef.current = client.subscribe(NOTIFICATION_USER_QUEUE, (message: IMessage) => {
          const activeUserId = userIdRef.current
          if (!activeUserId) {
            return
          }
          try {
            const payload = JSON.parse(message.body) as AppNotification
            if (!payload?.id) {
              return
            }
            applyNotificationPush(queryClient, activeUserId, {
              ...payload,
              orderId: payload.orderId ?? null,
              readAt: payload.readAt ?? null,
              unread: payload.unread ?? payload.readAt == null,
            })
          } catch {
            // Ignore malformed frames; REST remains source of truth.
          }
        })
        // Recover anything missed while disconnected.
        void queryClient.invalidateQueries({
          queryKey: notificationQueryKeys.root(userId),
        })
      },
      onDisconnect: () => {
        setConnected(false)
      },
      onWebSocketClose: () => {
        setConnected(false)
        if (cancelled) {
          return
        }
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
          return
        }
        reconnectAttempts += 1
        const delay = Math.min(30_000, BASE_RECONNECT_MS * 2 ** (reconnectAttempts - 1))
        window.setTimeout(() => {
          if (!cancelled && clientRef.current === client) {
            void refreshAccessTokenSingleFlight().finally(() => {
              if (!cancelled && !client.active) {
                client.activate()
              }
            })
          }
        }, delay)
      },
      onStompError: () => {
        setConnected(false)
      },
    })

    clientRef.current = client
    client.activate()

    return () => {
      cancelled = true
      tearDown(clientRef, subscriptionRef, setConnected)
    }
  }, [status, userId, queryClient])

  return { connected }
}

function tearDown(
  clientRef: { current: Client | null },
  subscriptionRef: { current: StompSubscription | null },
  setConnected: (value: boolean) => void,
): void {
  try {
    subscriptionRef.current?.unsubscribe()
  } catch {
    // ignore
  }
  subscriptionRef.current = null
  const client = clientRef.current
  clientRef.current = null
  if (client) {
    try {
      void client.deactivate()
    } catch {
      // ignore
    }
  }
  setConnected(false)
}
