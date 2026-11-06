import type { AppLocale } from '@/lib/i18n'

/** Admin category list names follow Accept-Language until T08 translation editors. */
export const adminCategoryQueryKeys = {
  all: ['admin', 'categories'] as const,
  list: (locale: AppLocale) => [...adminCategoryQueryKeys.all, 'list', locale] as const,
}
