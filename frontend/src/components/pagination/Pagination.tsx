import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

import { buildPaginationItems } from './build-pagination-items'
import { DEFAULT_LIST_PAGE_SIZE } from './constants'

export type PaginationProps = {
  /** 0-based current page index. */
  page: number
  totalPages: number
  totalElements: number
  /** When omitted, defaults to {@link DEFAULT_LIST_PAGE_SIZE} for hide logic. */
  pageSize?: number
  itemLabel?: string
  disabled?: boolean
  onPageChange: (page: number) => void
  className?: string
}

/**
 * Shared First / Previous / page numbers / ellipsis / Next / Last controls.
 * Hidden when there is at most one page or when totalElements ≤ pageSize.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  pageSize = DEFAULT_LIST_PAGE_SIZE,
  itemLabel,
  disabled = false,
  onPageChange,
  className,
}: PaginationProps) {
  const { t } = useTranslation('common')
  if (totalElements <= 0 || totalPages <= 1 || totalElements <= pageSize) {
    return null
  }

  const safePage = Math.min(Math.max(0, page), totalPages - 1)
  const items = buildPaginationItems(safePage, totalPages)
  const isFirst = safePage <= 0
  const isLast = safePage >= totalPages - 1

  return (
    <nav
      className={cn(
        // Shared chat-launcher safe area (CSS vars set by ChatPanel when mounted).
        'flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4',
        'pb-[max(0px,var(--chat-launcher-clearance-block,0px))]',
        'pr-[max(0px,var(--chat-launcher-clearance-inline,0px))]',
        className,
      )}
      aria-label={t('pagination.aria')}
    >
      <p className="text-sm text-muted-foreground" aria-live="polite">
        {t('pagination.summary', {
          page: safePage + 1,
          totalPages,
          total: totalElements,
          itemLabel: itemLabel ?? t('pagination.results'),
        })}
      </p>
      <div className="flex flex-wrap items-center gap-1.5">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || isFirst}
          aria-label={t('pagination.firstAria')}
          onClick={() => onPageChange(0)}
        >
          {t('pagination.first')}
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || isFirst}
          aria-label={t('pagination.previousAria')}
          onClick={() => onPageChange(safePage - 1)}
        >
          {t('pagination.previous')}
        </Button>
        {items.map((item, index) =>
          item === 'ellipsis' ? (
            <span
              key={`ellipsis-${index}`}
              className="px-1.5 text-sm text-muted-foreground"
              aria-hidden="true"
            >
              …
            </span>
          ) : (
            <Button
              key={item}
              type="button"
              variant={item === safePage ? 'default' : 'outline'}
              size="sm"
              disabled={disabled}
              aria-label={t('pagination.pageAria', { page: item + 1 })}
              aria-current={item === safePage ? 'page' : undefined}
              onClick={() => onPageChange(item)}
            >
              {item + 1}
            </Button>
          ),
        )}
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || isLast}
          aria-label={t('pagination.nextAria')}
          onClick={() => onPageChange(safePage + 1)}
        >
          {t('pagination.next')}
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || isLast}
          aria-label={t('pagination.lastAria')}
          onClick={() => onPageChange(totalPages - 1)}
        >
          {t('pagination.last')}
        </Button>
      </div>
    </nav>
  )
}
