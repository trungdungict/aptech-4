import { Button } from '@/components/ui/button'

type CatalogPaginationProps = {
  page: number
  totalPages: number
  totalElements: number
  disabled?: boolean
  onPageChange: (page: number) => void
}

export function CatalogPagination({
  page,
  totalPages,
  totalElements,
  disabled,
  onPageChange,
}: CatalogPaginationProps) {
  if (totalPages <= 0) {
    return null
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
      <p className="text-sm text-muted-foreground">
        Page {page + 1} of {totalPages} · {totalElements} products
      </p>
      <div className="flex gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          Previous
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={disabled || page >= totalPages - 1}
          onClick={() => onPageChange(page + 1)}
        >
          Next
        </Button>
      </div>
    </div>
  )
}
