import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { Pagination } from './Pagination'

describe('Pagination chat clearance', () => {
  it('reserves shared chat-launcher safe-area CSS variables', () => {
    render(
      <Pagination page={0} totalPages={3} totalElements={25} onPageChange={vi.fn()} />,
    )
    const nav = screen.getByRole('navigation', { name: 'Pagination' })
    expect(nav.className).toContain('--chat-launcher-clearance-inline')
    expect(nav.className).toContain('--chat-launcher-clearance-block')
  })
})

describe('Pagination', () => {
  it('hides controls when there are 10 or fewer records', () => {
    const { container } = render(
      <Pagination page={0} totalPages={1} totalElements={10} onPageChange={vi.fn()} />,
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('shows controls when there are 11 records across 2 pages', () => {
    render(
      <Pagination page={0} totalPages={2} totalElements={11} onPageChange={vi.fn()} />,
    )
    expect(screen.getByRole('navigation', { name: 'Pagination' })).toBeInTheDocument()
    expect(screen.getByText(/page 1 of 2/i)).toBeInTheDocument()
  })

  it('disables First and Previous on the first page', () => {
    render(
      <Pagination page={0} totalPages={5} totalElements={50} onPageChange={vi.fn()} />,
    )
    expect(screen.getByRole('button', { name: 'First page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Last page' })).toBeEnabled()
  })

  it('disables Next and Last on the last page', () => {
    render(
      <Pagination page={4} totalPages={5} totalElements={50} onPageChange={vi.fn()} />,
    )
    expect(screen.getByRole('button', { name: 'First page' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Last page' })).toBeDisabled()
  })

  it('renders ellipsis for large page counts', () => {
    render(
      <Pagination page={5} totalPages={12} totalElements={120} onPageChange={vi.fn()} />,
    )
    expect(screen.getAllByText('…').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByRole('button', { name: 'Page 6' })).toHaveAttribute('aria-current', 'page')
  })

  it('invokes onPageChange for First, page number, and Last', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(
      <Pagination page={2} totalPages={8} totalElements={80} onPageChange={onPageChange} />,
    )

    await user.click(screen.getByRole('button', { name: 'First page' }))
    expect(onPageChange).toHaveBeenCalledWith(0)

    await user.click(screen.getByRole('button', { name: 'Page 4' }))
    expect(onPageChange).toHaveBeenCalledWith(3)

    await user.click(screen.getByRole('button', { name: 'Last page' }))
    expect(onPageChange).toHaveBeenCalledWith(7)
  })
})
