/**
 * Incremental SSE parser that tolerates arbitrary chunk boundaries and UTF-8 splits.
 * Spec subset: `event:` / multiline `data:` / blank-line event terminator.
 */

export type SseParsedEvent =
  | { type: 'delta'; data: string }
  | { type: 'done'; data: string }
  | { type: 'error'; data: string }
  | { type: 'other'; event: string; data: string }

export class SseEventParser {
  private buffer = ''
  private eventName = 'message'
  private dataLines: string[] = []
  private readonly decoder = new TextDecoder('utf-8', { fatal: false })

  /** Feed the next network chunk (may be mid-codepoint). */
  push(bytes: Uint8Array): SseParsedEvent[] {
    this.buffer += this.decoder.decode(bytes, { stream: true })
    return this.drain(false)
  }

  /** Flush decoder + remaining buffer when the stream ends. */
  finish(): SseParsedEvent[] {
    this.buffer += this.decoder.decode()
    const events = this.drain(true)
    return events
  }

  private drain(flushPartialEvent: boolean): SseParsedEvent[] {
    const out: SseParsedEvent[] = []
    // Normalize CRLF so blank-line detection is reliable.
    this.buffer = this.buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n')

    while (true) {
      const sep = this.buffer.indexOf('\n\n')
      if (sep < 0) {
        break
      }
      const rawEvent = this.buffer.slice(0, sep)
      this.buffer = this.buffer.slice(sep + 2)
      const parsed = this.parseEventBlock(rawEvent)
      if (parsed) {
        out.push(parsed)
      }
    }

    if (flushPartialEvent && this.buffer.trim().length > 0) {
      const parsed = this.parseEventBlock(this.buffer)
      this.buffer = ''
      if (parsed) {
        out.push(parsed)
      }
    }

    return out
  }

  private parseEventBlock(block: string): SseParsedEvent | null {
    this.eventName = 'message'
    this.dataLines = []
    for (const line of block.split('\n')) {
      if (line.startsWith(':')) {
        continue // comment / keepalive
      }
      if (line.startsWith('event:')) {
        this.eventName = line.slice(6).trim() || 'message'
        continue
      }
      if (line.startsWith('data:')) {
        // Spec: optional single leading space after "data:"
        let value = line.slice(5)
        if (value.startsWith(' ')) {
          value = value.slice(1)
        }
        this.dataLines.push(value)
        continue
      }
    }
    if (this.dataLines.length === 0) {
      return null
    }
    const data = this.dataLines.join('\n')
    const name = this.eventName
    this.eventName = 'message'
    this.dataLines = []
    if (name === 'delta') {
      // Backend may JSON-encode delta strings so leading spaces/newlines survive SSE
      // `data:` leading-space stripping. Plain-text deltas remain supported.
      return { type: 'delta', data: decodeDeltaPayload(data) }
    }
    if (name === 'done') {
      return { type: 'done', data }
    }
    if (name === 'error') {
      return { type: 'error', data }
    }
    return { type: 'other', event: name, data }
  }
}

/** Decode a JSON-string delta (`" text"`) or return the raw SSE data payload. */
export function decodeDeltaPayload(data: string): string {
  if (!data.startsWith('"')) {
    return data
  }
  try {
    const parsed: unknown = JSON.parse(data)
    return typeof parsed === 'string' ? parsed : data
  } catch {
    return data
  }
}
