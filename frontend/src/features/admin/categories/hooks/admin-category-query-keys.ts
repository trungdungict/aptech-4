export const adminCategoryQueryKeys = {
  all: ['admin', 'categories'] as const,
  list: () => [...adminCategoryQueryKeys.all, 'list'] as const,
}
