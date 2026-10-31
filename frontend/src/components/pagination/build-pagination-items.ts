export type PaginationItem = number | 'ellipsis'

function range(start: number, end: number): number[] {
  const items: number[] = []
  for (let page = start; page <= end; page += 1) {
    items.push(page)
  }
  return items
}

/**
 * Builds a compact page-number sequence with ellipsis gaps.
 * `currentPage` and returned page numbers are 0-based.
 */
export function buildPaginationItems(
  currentPage: number,
  totalPages: number,
  siblingCount = 1,
): PaginationItem[] {
  if (totalPages <= 0) {
    return []
  }

  const current = Math.min(Math.max(0, currentPage), totalPages - 1)
  const siblings = Math.max(0, siblingCount)
  // first + last + current + 2*siblings + 2 ellipsis slots
  const maxVisible = siblings * 2 + 5

  if (totalPages <= maxVisible) {
    return range(0, totalPages - 1)
  }

  const leftSibling = Math.max(current - siblings, 0)
  const rightSibling = Math.min(current + siblings, totalPages - 1)

  const showLeftEllipsis = leftSibling > 1
  const showRightEllipsis = rightSibling < totalPages - 2

  if (!showLeftEllipsis && showRightEllipsis) {
    const leftItemCount = 3 + 2 * siblings
    return [...range(0, leftItemCount - 1), 'ellipsis', totalPages - 1]
  }

  if (showLeftEllipsis && !showRightEllipsis) {
    const rightItemCount = 3 + 2 * siblings
    return [0, 'ellipsis', ...range(totalPages - rightItemCount, totalPages - 1)]
  }

  return [0, 'ellipsis', ...range(leftSibling, rightSibling), 'ellipsis', totalPages - 1]
}
