import { describe, expect, it } from 'vitest'

import { buildPaginationItems } from './build-pagination-items'
import { DEFAULT_LIST_PAGE_SIZE } from './constants'
import { clampPage, parsePageParam, writePageParam } from './parse-page-param'

describe('DEFAULT_LIST_PAGE_SIZE', () => {
  it('is 10', () => {
    expect(DEFAULT_LIST_PAGE_SIZE).toBe(10)
  })
})

describe('buildPaginationItems', () => {
  it('returns all pages when the range is small', () => {
    expect(buildPaginationItems(0, 5)).toEqual([0, 1, 2, 3, 4])
  })

  it('includes ellipsis for large page counts', () => {
    expect(buildPaginationItems(0, 12)).toEqual([0, 1, 2, 3, 4, 'ellipsis', 11])
    expect(buildPaginationItems(5, 12)).toEqual([0, 'ellipsis', 4, 5, 6, 'ellipsis', 11])
    expect(buildPaginationItems(11, 12)).toEqual([0, 'ellipsis', 7, 8, 9, 10, 11])
  })
})

describe('page param helpers', () => {
  it('parses and clamps page values', () => {
    expect(parsePageParam(null)).toBe(0)
    expect(parsePageParam('2')).toBe(2)
    expect(parsePageParam('-1')).toBe(0)
    expect(clampPage(99, 3)).toBe(2)
    expect(clampPage(-1, 3)).toBe(0)
  })

  it('omits page=0 from search params', () => {
    const params = new URLSearchParams('q=milk&page=3')
    expect(writePageParam(params, 0).toString()).toBe('q=milk')
    expect(writePageParam(params, 2).get('page')).toBe('2')
  })
})
