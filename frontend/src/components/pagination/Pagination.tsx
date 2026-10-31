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
  itemLabel = 'results',
  disabled = false,
  onPageChange,
  className,
}: PaginationProps) {
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
      aria-label="Pagination"
    >
      <p className="text-sm text-muted-foreground" aria-live="polite">
        Page {safePage + 1} of {totalPages} · {totalElements} {itemLabel}
      </p>
      <div className="flex flex-wrap items-center gap-1.5">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || isFirst}
          aria-label="First page"
          onClick={() => onPageChange(0)}
        >
          First
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || isFirst}
          aria-label="Previous page"
          onClick={() => onPageChange(safePage - 1)}
        >
          Previous
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
              aria-label={`Page ${item + 1}`}
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
          aria-label="Next page"
          onClick={() => onPageChange(safePage + 1)}
        >
          Next
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || isLast}
          aria-label="Last page"
          onClick={() => onPageChange(totalPages - 1)}
        >
          Last
        </Button>
      </div>
    </nav>
  )
}
