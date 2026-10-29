import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { useAuth } from '@/features/auth/hooks/useAuth'
import { ApiClientError } from '@/lib/api/api-error'

import * as notificationsApi from '../api/notifications-api'
import { notificationQueryKeys } from '../hooks/notification-query-keys'
import { formatNotificationTime } from '../lib/format-notification-time'
import { notificationOrderPath } from '../lib/notification-order-path'
import { useNotificationRealtimeContext } from '../hooks/useNotificationRealtimeContext'
import type { AppNotification } from '../types'
import { NOTIFICATION_DEFAULT_PAGE_SIZE, NOTIFICATION_FALLBACK_POLL_MS } from '../types'

export function NotificationBell() {
  const { status, user } = useAuth()
  const userId = user?.id
  const enabled = status === 'authenticated' && Boolean(userId)
  const { connected: wsConnected } = useNotificationRealtimeContext()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const panelId = useId()
  const rootRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [page, setPage] = useState(0)
  const fallbackPoll = enabled && !wsConnected ? NOTIFICATION_FALLBACK_POLL_MS : false

  useEffect(() => {
    if (!open) {
      return
    }
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const unreadQuery = useQuery({
    queryKey: userId ? notificationQueryKeys.unreadCount(userId) : ['notifications', 'anon'],
    queryFn: ({ signal }) => notificationsApi.getUnreadCount(signal),
    enabled,
    refetchInterval: fallbackPoll,
    refetchIntervalInBackground: false,
  })

  const listQuery = useQuery({
    queryKey: userId
      ? notificationQueryKeys.list(userId, page, NOTIFICATION_DEFAULT_PAGE_SIZE)
      : ['notifications', 'anon', 'list'],
    queryFn: ({ signal }) =>
      notificationsApi.listNotifications(page, NOTIFICATION_DEFAULT_PAGE_SIZE, signal),
    enabled: enabled && open,
    refetchInterval: open ? fallbackPoll : false,
    refetchIntervalInBackground: false,
  })

  const invalidateMine = async () => {
    if (!userId) {
      return
    }
    await queryClient.invalidateQueries({ queryKey: notificationQueryKeys.root(userId) })
  }

  const markOneMutation = useMutation({
    mutationFn: (id: string) => notificationsApi.markNotificationRead(id),
    onSuccess: async () => {
      await invalidateMine()
    },
  })

  const markAllMutation = useMutation({
    mutationFn: () => notificationsApi.markAllNotificationsRead(),
    onSuccess: async () => {
      await invalidateMine()
    },
  })

  if (!enabled) {
    return null
  }

  const unreadCount = unreadQuery.data?.unreadCount ?? 0
  const badgeLabel = unreadCount > 99 ? '99+' : String(unreadCount)

  const onItemActivate = async (item: AppNotification) => {
    if (item.unread) {
      try {
        await markOneMutation.mutateAsync(item.id)
      } catch {
        // Navigation still proceeds; unread state refreshes on next poll.
      }
    }
    setOpen(false)
    if (item.orderId) {
      void navigate(notificationOrderPath(item.orderId, user?.roles))
    }
  }

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        className="relative rounded-lg px-2.5 py-1.5 text-foreground hover:bg-muted focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        aria-label={
          unreadCount > 0
            ? `Notifications, ${unreadCount} unread`
            : 'Notifications'
        }
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((value) => !value)}
      >
        <BellIcon />
        {unreadCount > 0 ? (
          <span className="absolute -top-0.5 -right-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] font-semibold text-primary-foreground">
            {badgeLabel}
          </span>
        ) : null}
      </button>

      {open ? (
        <div
          id={panelId}
          role="dialog"
          aria-label="Notifications"
          className="absolute right-0 z-40 mt-2 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-border bg-card shadow-lg"
        >
          <div className="flex items-center justify-between gap-2 border-b border-border px-4 py-3">
            <p className="text-sm font-semibold text-foreground">Notifications</p>
            <button
              type="button"
              className="text-xs font-medium text-primary hover:underline disabled:opacity-50"
              disabled={markAllMutation.isPending || unreadCount === 0}
              onClick={() => {
                void markAllMutation.mutateAsync()
              }}
            >
              Mark all as read
            </button>
          </div>

          <div className="max-h-[min(24rem,60vh)] overflow-y-auto">
            {listQuery.isLoading ? (
              <p className="px-4 py-8 text-center text-sm text-muted-foreground" role="status">
                Loading notifications…
              </p>
            ) : null}

            {listQuery.isError ? (
              <div className="space-y-3 px-4 py-8 text-center">
                <p className="text-sm text-muted-foreground">
                  {listQuery.error instanceof ApiClientError
                    ? listQuery.error.userMessage
                    : 'Could not load notifications.'}
                </p>
                <button
                  type="button"
                  className="rounded-lg border border-border px-3 py-1.5 text-sm font-medium hover:bg-muted"
                  onClick={() => {
                    void listQuery.refetch()
                  }}
                >
                  Retry
                </button>
              </div>
            ) : null}

            {listQuery.isSuccess && listQuery.data.items.length === 0 ? (
              <p className="px-4 py-8 text-center text-sm text-muted-foreground">
                No notifications yet.
              </p>
            ) : null}

            {listQuery.isSuccess
              ? listQuery.data.items.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={`flex w-full flex-col gap-1 border-b border-border px-4 py-3 text-left last:border-b-0 hover:bg-muted/60 ${
                      item.unread ? 'bg-card' : 'bg-muted/30'
                    }`}
                    onClick={() => {
                      void onItemActivate(item)
                    }}
                  >
                    <div className="flex items-start gap-2">
                      {item.unread ? (
                        <span
                          className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-primary"
                          aria-hidden="true"
                        />
                      ) : (
                        <span className="mt-1.5 h-2 w-2 shrink-0" aria-hidden="true" />
                      )}
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-semibold text-foreground">{item.title}</p>
                        <p className="mt-0.5 text-sm text-muted-foreground">{item.message}</p>
                        <p className="mt-1 text-xs text-muted-foreground">
                          {formatNotificationTime(item.createdAt)}
                        </p>
                      </div>
                    </div>
                  </button>
                ))
              : null}
          </div>

          {listQuery.isSuccess && (!listQuery.data.last || page > 0) ? (
            <div className="flex gap-2 border-t border-border px-4 py-2">
              {page > 0 ? (
                <button
                  type="button"
                  className="flex-1 rounded-lg px-3 py-1.5 text-sm font-medium text-muted-foreground hover:bg-muted"
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  Newer
                </button>
              ) : null}
              {!listQuery.data.last ? (
                <button
                  type="button"
                  className="flex-1 rounded-lg px-3 py-1.5 text-sm font-medium text-primary hover:bg-muted"
                  onClick={() => setPage((current) => current + 1)}
                >
                  Older
                </button>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

function BellIcon() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-5 w-5"
      aria-hidden="true"
    >
      <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
      <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
    </svg>
  )
}
