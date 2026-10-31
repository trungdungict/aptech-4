import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'

import { Pagination } from '@/components/pagination'
import {
  CHAT_LAUNCHER_CLEARANCE_BLOCK,
  CHAT_LAUNCHER_CLEARANCE_INLINE,
} from '../lib/chat-launcher'

import { ChatPanel } from './ChatPanel'

vi.mock('../api/chat-api', () => ({
  getChatHealth: vi.fn(async () => ({
    enabled: false,
    modelConfigured: false,
    model: 'llama3.2',
    status: 'DISABLED',
    detail: 'off',
  })),
  streamChatMessage: vi.fn(),
  sendChatMessage: vi.fn(),
}))

describe('ChatPanel', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    document.documentElement.style.removeProperty(CHAT_LAUNCHER_CLEARANCE_INLINE)
    document.documentElement.style.removeProperty(CHAT_LAUNCHER_CLEARANCE_BLOCK)
  })

  afterEach(() => {
    document.documentElement.style.removeProperty(CHAT_LAUNCHER_CLEARANCE_INLINE)
    document.documentElement.style.removeProperty(CHAT_LAUNCHER_CLEARANCE_BLOCK)
  })

  it('opens with English greeting by default', async () => {
    const user = userEvent.setup()
    render(<ChatPanel variant="customer" />)
    await user.click(screen.getByRole('button', { name: /Aptech Assistant/i }))
    expect(screen.getByText(/Hello! I can help with your orders/i)).toBeInTheDocument()
  })

  it('minimizes the launcher and can reopen the assistant', async () => {
    const user = userEvent.setup()
    render(<ChatPanel variant="customer" />)

    await user.click(screen.getByRole('button', { name: /minimize assistant/i }))
    expect(screen.queryByRole('button', { name: /^Aptech Assistant$/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /show aptech assistant/i })).toBeInTheDocument()
    expect(document.documentElement.style.getPropertyValue(CHAT_LAUNCHER_CLEARANCE_INLINE)).toBe(
      '3.5rem',
    )

    await user.click(screen.getByRole('button', { name: /show aptech assistant/i }))
    expect(screen.getByText(/Hello! I can help with your orders/i)).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /close chat/i }).length).toBeGreaterThan(0)
  })

  it('keeps pagination controls interactive while the launcher is present', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(
      <div>
        <Pagination
          page={0}
          totalPages={5}
          totalElements={50}
          onPageChange={onPageChange}
        />
        <ChatPanel variant="customer" />
      </div>,
    )

    const nav = screen.getByRole('navigation', { name: 'Pagination' })
    expect(nav.className).toMatch(/pr-\[max\(0px,var\(--chat-launcher-clearance-inline/)
    expect(nav.className).toMatch(/pb-\[max\(0px,var\(--chat-launcher-clearance-block/)
    expect(document.documentElement.style.getPropertyValue(CHAT_LAUNCHER_CLEARANCE_INLINE)).toBe(
      '4.75rem',
    )

    await user.click(screen.getByRole('button', { name: 'Last page' }))
    expect(onPageChange).toHaveBeenCalledWith(4)

    const root = screen.getByTestId('chat-launcher-root')
    expect(root.className).toMatch(/max-w-\[calc\(100vw-1\.5rem\)\]/)
    expect(root.className).not.toMatch(/overflow-x/)
  })

  it('switches UI copy to Vietnamese when selected', async () => {
    const user = userEvent.setup()
    render(<ChatPanel variant="customer" />)
    await user.click(screen.getByRole('button', { name: /Aptech Assistant/i }))
    await user.selectOptions(screen.getByLabelText(/Language/i), 'VI')
    expect(screen.getByText(/Xin chào!/i)).toBeInTheDocument()
  })

  it('keeps message list scrollable and composer visible', async () => {
    const user = userEvent.setup()
    render(<ChatPanel variant="admin" />)
    await user.click(screen.getByRole('button', { name: /Aptech Assistant/i }))

    const list = screen.getByTestId('chat-message-list')
    expect(list.className).toMatch(/min-h-\[7rem\]/)
    expect(list.className).toMatch(/overflow-y-auto/)
    expect(list.className).toMatch(/flex-1/)
    expect(list.className).toMatch(/min-w-0/)

    const panel = list.closest('section')
    expect(panel?.className).toMatch(/max-h-\[calc\(100dvh-5\.5rem\)\]/)

    const suggestions = screen.getByTestId('chat-suggestions')
    expect(suggestions).toBeInTheDocument()
    expect(suggestions.className).toMatch(/overflow-x-auto/)
    expect(suggestions.className).not.toMatch(/flex-wrap/)
    const composer = screen.getByTestId('chat-composer')
    expect(composer).toBeInTheDocument()
    expect(composer.className).toMatch(/shrink-0/)
    expect(screen.getByRole('textbox', { name: /Ask a question/i })).toBeEnabled()
  })

  it('wraps long assistant messages without horizontal overflow classes', async () => {
    const { streamChatMessage } = await import('../api/chat-api')
    const longReply =
      'Here are the available products under 2000000 VND:\n' +
      '1. Demo Ceramic Mug — 120000 VND (/products/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee)\n' +
      'https://example.com/' +
      'x'.repeat(120)

    vi.mocked(streamChatMessage).mockImplementation(async (_input, handlers) => {
      handlers.onDelta(longReply.slice(0, 40))
      handlers.onDelta(longReply.slice(40))
      handlers.onDone({
        conversationId: 'c1',
        reply: longReply,
        language: 'EN',
        suggestedPrompts: [],
        timing: { path: 'deterministic_products_under' },
      })
    })

    const user = userEvent.setup()
    render(<ChatPanel variant="customer" />)
    await user.click(screen.getByRole('button', { name: /Aptech Assistant/i }))
    await user.type(screen.getByRole('textbox', { name: /Ask a question/i }), 'Show products')
    await user.click(screen.getByRole('button', { name: /^Send$/i }))

    const bubble = await screen.findByTestId('chat-assistant-bubble')
    expect(bubble.textContent).toContain('Demo Ceramic Mug')
    expect(bubble.textContent).toMatch(/1\.\s+Demo Ceramic Mug/)
    expect(bubble.className).toMatch(/min-w-0/)
    expect(bubble.className).toMatch(/max-w-full/)
    expect(bubble.className).toMatch(/break-words/)
    expect(bubble.className).toMatch(/whitespace-pre-wrap/)
    expect(bubble.className).not.toMatch(/break-all/)
  })
})
