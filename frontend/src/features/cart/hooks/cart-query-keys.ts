/** Query keys include userId so caches cannot leak across sessions. Never include tokens. */
export const cartQueryKeys = {
  all: ['cart'] as const,
  detail: (userId: string) => ['cart', userId] as const,
}
