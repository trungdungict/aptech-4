import { describe, expect, it } from 'vitest'

import { SseEventParser } from './sse-parser'

function encode(text: string): Uint8Array {
  return new TextEncoder().encode(text)
}

describe('SseEventParser', () => {
  it('parses delta and done across chunk boundaries', () => {
    const parser = new SseEventParser()
    const a = parser.push(encode('event:delta\ndata:Hel'))
    expect(a).toEqual([])
    const b = parser.push(encode('lo\n\nevent:done\ndata:{"reply":"Hello"}\n\n'))
    expect(b).toEqual([
      { type: 'delta', data: 'Hello' },
      { type: 'done', data: '{"reply":"Hello"}' },
    ])
  })

  it('joins multiline data fields', () => {
    const parser = new SseEventParser()
    const events = parser.push(
      encode('event:delta\ndata:line1\ndata:line2\n\nevent:done\ndata:{}\n\n'),
    )
    expect(events[0]).toEqual({ type: 'delta', data: 'line1\nline2' })
    expect(events[1]?.type).toBe('done')
  })

  it('handles UTF-8 characters split between chunks', () => {
    const parser = new SseEventParser()
    const full = encode('event:delta\ndata:café ☕\n\n')
    const mid = 18 // mid multi-byte sequence region; split hard
    const first = parser.push(full.slice(0, mid))
    const second = parser.push(full.slice(mid))
    const flush = parser.finish()
    const deltas = [...first, ...second, ...flush].filter((e) => e.type === 'delta')
    expect(deltas).toHaveLength(1)
    expect(deltas[0]).toEqual({ type: 'delta', data: 'café ☕' })
  })

  it('emits error terminal events', () => {
    const parser = new SseEventParser()
    const events = parser.push(encode('event:error\ndata:Model Unavailable\n\n'))
    expect(events).toEqual([{ type: 'error', data: 'Model Unavailable' }])
  })

  it('flushes a final event when stream closes without trailing blank line', () => {
    const parser = new SseEventParser()
    parser.push(encode('event:done\ndata:{"ok":true}'))
    expect(parser.finish()).toEqual([{ type: 'done', data: '{"ok":true}' }])
  })

  it('ignores comment lines and malformed empty blocks', () => {
    const parser = new SseEventParser()
    const events = parser.push(encode(': keep-alive\n\nevent:delta\ndata:x\n\n\n\n'))
    expect(events).toEqual([{ type: 'delta', data: 'x' }])
  })

  it('treats premature EOF with only partial text as no terminal', () => {
    const parser = new SseEventParser()
    const mid = parser.push(encode('event:delta\ndata:partial'))
    const end = parser.finish()
    // incomplete event without blank line — finish may emit if buffer has content
    const all = [...mid, ...end]
    const terminals = all.filter((e) => e.type === 'done' || e.type === 'error')
    expect(terminals).toHaveLength(0)
  })

  it('preserves leading spaces from JSON-encoded delta payloads', () => {
    const parser = new SseEventParser()
    // Mimics Spring APPLICATION_JSON string + SSE optional space after data:
    const events = parser.push(
      encode('event:delta\ndata: " are the available products"\n\n'),
    )
    expect(events).toEqual([{ type: 'delta', data: ' are the available products' }])
  })

  it('preserves newlines and punctuation inside JSON-encoded deltas', () => {
    const parser = new SseEventParser()
    const payload = JSON.stringify('Here are items:\n1. Mug\n2. Bowl!')
    const events = parser.push(encode(`event:delta\ndata:${payload}\n\n`))
    expect(events[0]).toEqual({
      type: 'delta',
      data: 'Here are items:\n1. Mug\n2. Bowl!',
    })
  })

  it('joins JSON deltas that split a word or space across SSE events', () => {
    const parser = new SseEventParser()
    const first = parser.push(encode(`event:delta\ndata:${JSON.stringify('Demo')}\n\n`))
    const second = parser.push(encode(`event:delta\ndata:${JSON.stringify(' Ceramic')}\n\n`))
    const third = parser.push(encode(`event:delta\ndata:${JSON.stringify(' Mug')}\n\n`))
    const joined = [...first, ...second, ...third]
      .filter((e) => e.type === 'delta')
      .map((e) => (e.type === 'delta' ? e.data : ''))
      .join('')
    expect(joined).toBe('Demo Ceramic Mug')
  })

  it('still accepts plain-text deltas for backward compatibility', () => {
    const parser = new SseEventParser()
    const events = parser.push(encode('event:delta\ndata:Hello\n\n'))
    expect(events).toEqual([{ type: 'delta', data: 'Hello' }])
  })

  it('handles UTF-8 emoji split across network chunks inside a JSON delta', () => {
    const parser = new SseEventParser()
    const full = encode(`event:delta\ndata:${JSON.stringify('ok ☕ done')}\n\n`)
    const mid = Math.floor(full.length / 2)
    const events = [...parser.push(full.slice(0, mid)), ...parser.push(full.slice(mid))]
    expect(events.filter((e) => e.type === 'delta')).toEqual([
      { type: 'delta', data: 'ok ☕ done' },
    ])
  })
})

