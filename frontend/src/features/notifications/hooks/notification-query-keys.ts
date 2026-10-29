export const notificationQueryKeys = {
  all: ['notifications'] as const,
  root: (userId: string) => ['notifications', userId] as const,
  lists: (userId: string) => ['notifications', userId, 'list'] as const,
  list: (userId: string, page: number, size: number) =>
    ['notifications', userId, 'list', { page, size }] as const,
  unreadCount: (userId: string) => ['notifications', userId, 'unread-count'] as const,
}
