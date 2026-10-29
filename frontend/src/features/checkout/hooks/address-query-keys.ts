export const addressQueryKeys = {
  all: ['addresses'] as const,
  list: (userId: string) => ['addresses', userId, 'list'] as const,
}
