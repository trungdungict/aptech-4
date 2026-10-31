import { useEffect, useMemo } from 'react'
import { useSearchParams } from 'react-router-dom'

import { DEFAULT_LIST_PAGE_SIZE } from './constants'
import { clampPage, parsePageParam, writePageParam } from './parse-page-param'

type ClientListPaginationResult<T> = {
  page: number
  totalPages: number
  totalElements: number
  pageItems: T[]
  pageSize: number
  goToPage: (page: number) => void
}

/**
 * Client-side slice of a fully loaded list, synced to `?page=` (0-based, omitted on page 0).
 */
export function useClientListPagination<T>(
  items: T[] | undefined,
  pageSize: number = DEFAULT_LIST_PAGE_SIZE,
): ClientListPaginationResult<T> {
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedPage = parsePageParam(searchParams.get('page'))

  const totalElements = items?.length ?? 0
  const totalPages = Math.max(1, Math.ceil(totalElements / pageSize) || 1)
  const page = clampPage(requestedPage, totalPages)

  useEffect(() => {
    if (!items || requestedPage === page) {
      return
    }
    setSearchParams(writePageParam(searchParams, page), { replace: true })
  }, [items, requestedPage, page, searchParams, setSearchParams])

  const pageItems = useMemo(() => {
    if (!items) {
      return []
    }
    const start = page * pageSize
    return items.slice(start, start + pageSize)
  }, [items, page, pageSize])

  const goToPage = (nextPage: number) => {
    setSearchParams(writePageParam(searchParams, clampPage(nextPage, totalPages)))
  }

  return {
    page,
    totalPages,
    totalElements,
    pageItems,
    pageSize,
    goToPage,
  }
}
