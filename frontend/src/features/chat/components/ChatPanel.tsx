import { useEffect, useId, useRef, useState } from 'react'

import { ApiClientError } from '@/lib/api/api-error'

import { getChatHealth, streamChatMessage } from '../api/chat-api'
import { useChatLanguage } from '../hooks/useChatLanguage'
import { chatCopy, defaultSuggestedPrompts } from '../lib/chat-i18n'
import type { ChatHealth, ChatMessage } from '../types'

type ChatPanelProps = {
  variant: 'customer' | 'admin'
}

export function ChatPanel({ variant }: ChatPanelProps) {
  const { language, setLanguage } = useChatLanguage()
  const copy = chatCopy(language)
  const admin = variant === 'admin'
  const titleId = useId()
  const [open, setOpen] = useState(false)
  const [health, setHealth] = useState<ChatHealth | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [draft, setDraft] = useState('')
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [interruptedPrompt, setInterruptedPrompt] = useState<string | null>(null)
  const [customSuggestions, setCustomSuggestions] = useState<string[] | null>(null)
  const suggestions = customSuggestions ?? defaultSuggestedPrompts(language, admin)
  const listRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<AbortController | null>(null)
  const requestSeqRef = useRef(0)

  useEffect(() => {
    if (!open) {
      return
    }
    const controller = new AbortController()
    void getChatHealth(controller.signal)
      .then(setHealth)
      .catch(() => {
        setHealth({
          enabled: false,
          modelConfigured: false,
          model: '',
          status: 'UNAVAILABLE',
          detail: copy.unavailable,
        })
      })
    return () => controller.abort()
  }, [open, copy.unavailable])

  useEffect(() => {
    listRef.current?.scrollTo?.({ top: listRef.current.scrollHeight })
  }, [messages, pending])

  useEffect(() => {
    return () => {
      abortRef.current?.abort()
    }
  }, [])

  const clearChat = () => {
    abortRef.current?.abort()
    requestSeqRef.current += 1
    setMessages([])
    setConversationId(null)
    setError(null)
    setInterruptedPrompt(null)
    setDraft('')
    setCustomSuggestions(null)
  }

  const send = async (text: string) => {
    const message = text.trim()
    if (!message || pending) {
      return
    }
    setError(null)
    setInterruptedPrompt(null)
    setDraft('')
    const userMessage: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: message,
    }
    const assistantId = crypto.randomUUID()
    setMessages((current) => [
      ...current,
      userMessage,
      { id: assistantId, role: 'assistant', content: '' },
    ])
    setPending(true)
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    const seq = ++requestSeqRef.current
    const isCurrent = () => seq === requestSeqRef.current && !controller.signal.aborted

    try {
      await streamChatMessage(
        {
          message,
          conversationId,
          language,
          signal: controller.signal,
        },
        {
          onDelta: (chunk) => {
            if (!isCurrent()) {
              return
            }
            setMessages((current) =>
              current.map((item) =>
                item.id === assistantId ? { ...item, content: item.content + chunk } : item,
              ),
            )
          },
          onDone: (response) => {
            if (!isCurrent()) {
              return
            }
            setConversationId(response.conversationId)
            setCustomSuggestions(
              response.suggestedPrompts?.length ? response.suggestedPrompts : null,
            )
            setMessages((current) =>
              current.map((item) =>
                item.id === assistantId
                  ? {
                      ...item,
                      content: response.reply || item.content,
                      interrupted: false,
                    }
                  : item,
              ),
            )
          },
          onError: (err) => {
            if (!isCurrent()) {
              return
            }
            setError(err)
          },
          onInterrupted: () => {
            if (!isCurrent()) {
              return
            }
            setInterruptedPrompt(message)
            setError(copy.interrupted)
            setMessages((current) =>
              current.map((item) =>
                item.id === assistantId ? { ...item, interrupted: true } : item,
              ),
            )
          },
        },
      )
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        return
      }
      if (!isCurrent()) {
        return
      }
      setError(err instanceof ApiClientError ? err.userMessage : copy.unavailable)
    } finally {
      if (seq === requestSeqRef.current) {
        setPending(false)
        abortRef.current = null
      }
    }
  }

  return (
    <div className="fixed right-4 bottom-4 z-50 flex flex-col items-end gap-2">
      {open ? (
        <section
          aria-labelledby={titleId}
          className="flex max-h-[calc(100dvh-5.5rem)] h-[min(32rem,calc(100dvh-5.5rem))] w-[min(24rem,calc(100vw-2rem))] flex-col overflow-hidden rounded-2xl border border-border bg-card shadow-xl"
        >
          <header className="flex shrink-0 items-start justify-between gap-2 border-b border-border px-4 py-3">
            <div className="min-w-0">
              <h2 id={titleId} className="text-sm font-semibold text-foreground">
                {copy.title}
              </h2>
              <p className="mt-0.5 text-xs text-muted-foreground">{copy.subtitle}</p>
            </div>
            <button
              type="button"
              className="rounded-lg px-2 py-1 text-sm text-muted-foreground hover:bg-muted"
              onClick={() => setOpen(false)}
              aria-label="Close chat"
            >
              ✕
            </button>
          </header>

          <div className="flex shrink-0 items-center gap-2 border-b border-border px-4 py-2 text-xs">
            <label className="text-muted-foreground" htmlFor={`${titleId}-lang`}>
              {copy.language}
            </label>
            <select
              id={`${titleId}-lang`}
              className="rounded-md border border-border bg-background px-2 py-1"
              value={language}
              onChange={(event) => setLanguage(event.target.value === 'VI' ? 'VI' : 'EN')}
            >
              <option value="EN">{copy.english}</option>
              <option value="VI">{copy.vietnamese}</option>
            </select>
            <button
              type="button"
              className="ml-auto text-muted-foreground hover:underline"
              onClick={clearChat}
            >
              {copy.clear}
            </button>
          </div>

          {/* min-h-0 required so flex-1 can shrink and scroll inside a fixed-height column */}
          <div
            ref={listRef}
            data-testid="chat-message-list"
            className="min-h-[7rem] min-w-0 flex-1 space-y-3 overflow-x-hidden overflow-y-auto overscroll-contain px-4 py-3"
          >
            {messages.length === 0 ? (
              <p className="text-sm text-muted-foreground">{copy.greeting}</p>
            ) : null}
            {messages.map((message) => (
              <div key={message.id} className="min-w-0 space-y-1">
                <div
                  data-testid={
                    message.role === 'assistant' ? 'chat-assistant-bubble' : 'chat-user-bubble'
                  }
                  className={
                    message.role === 'user'
                      ? 'ml-8 min-w-0 max-w-full break-words rounded-xl bg-primary px-3 py-2 text-sm text-primary-foreground [overflow-wrap:anywhere]'
                      : 'mr-4 min-w-0 max-w-full break-words whitespace-pre-wrap rounded-xl bg-muted px-3 py-2 text-sm text-foreground [overflow-wrap:anywhere]'
                  }
                >
                  {message.content || (pending ? copy.loading : '')}
                </div>
                {message.interrupted ? (
                  <p className="mr-4 text-xs text-muted-foreground">{copy.interrupted}</p>
                ) : null}
              </div>
            ))}
            {error && !interruptedPrompt ? (
              <p className="text-sm text-destructive">{error}</p>
            ) : null}
            {interruptedPrompt ? (
              <div className="flex flex-wrap items-center gap-2">
                <p className="text-sm text-destructive">{error ?? copy.interrupted}</p>
                <button
                  type="button"
                  className="rounded-md border border-border px-2 py-1 text-xs hover:bg-muted"
                  disabled={pending}
                  onClick={() => {
                    const prompt = interruptedPrompt
                    setInterruptedPrompt(null)
                    setError(null)
                    void send(prompt)
                  }}
                >
                  {copy.retryLast}
                </button>
              </div>
            ) : null}
            {health && health.status === 'DISABLED' ? (
              <p className="text-sm text-muted-foreground">{copy.disabled}</p>
            ) : null}
          </div>

          {suggestions.length > 0 ? (
            <div
              data-testid="chat-suggestions"
              className="flex shrink-0 gap-2 overflow-x-auto border-t border-border bg-card px-3 py-2"
            >
              {suggestions.map((prompt) => (
                <button
                  key={prompt}
                  type="button"
                  className="shrink-0 whitespace-nowrap rounded-full border border-border px-2.5 py-1 text-left text-xs hover:bg-muted"
                  disabled={pending}
                  onClick={() => {
                    void send(prompt)
                  }}
                >
                  {prompt}
                </button>
              ))}
            </div>
          ) : null}

          <form
            data-testid="chat-composer"
            className="flex shrink-0 gap-2 border-t border-border bg-card p-3"
            onSubmit={(event) => {
              event.preventDefault()
              void send(draft)
            }}
          >
            <input
              className="min-w-0 flex-1 rounded-lg border border-border bg-background px-3 py-2 text-sm"
              value={draft}
              placeholder={copy.placeholder}
              disabled={pending}
              onChange={(event) => setDraft(event.target.value)}
              aria-label={copy.placeholder}
            />
            <button
              type="submit"
              className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
              disabled={pending || !draft.trim()}
            >
              {copy.send}
            </button>
          </form>
        </section>
      ) : null}

      <button
        type="button"
        className="rounded-full bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground shadow-lg hover:opacity-95"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
      >
        {open ? 'Close' : copy.title}
      </button>
    </div>
  )
}
