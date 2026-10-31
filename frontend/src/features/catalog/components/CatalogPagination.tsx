import { DEFAULT_LIST_PAGE_SIZE, Pagination } from '@/components/pagination'

type CatalogPaginationProps = {
  page: number
  totalPages: number
  totalElements: number
  pageSize?: number
  disabled?: boolean
  onPageChange: (page: number) => void
}

/** Catalog list pagination — shared First/Previous/numbers/Next/Last controls. */
export function CatalogPagination({
  page,
  totalPages,
  totalElements,
  pageSize = DEFAULT_LIST_PAGE_SIZE,
  disabled,
  onPageChange,
}: CatalogPaginationProps) {
  return (
    <Pagination
      page={page}
      totalPages={totalPages}
      totalElements={totalElements}
      pageSize={pageSize}
      itemLabel="products"
      disabled={disabled}
      onPageChange={onPageChange}
    />
  )
}
