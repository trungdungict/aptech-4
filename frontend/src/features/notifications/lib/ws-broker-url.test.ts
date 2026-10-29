import { afterEach, describe, expect, it, vi } from 'vitest'

import { wsBrokerUrl } from './ws-broker-url'

describe('wsBrokerUrl', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('derives same-origin /ws from window when API is path-absolute', () => {
    vi.stubGlobal('window', {
      location: { protocol: 'http:', host: 'localhost:8088' },
    })
    expect(wsBrokerUrl('/api/v1')).toBe('ws://localhost:8088/ws')
  })

  it('uses wss on https pages', () => {
    vi.stubGlobal('window', {
      location: { protocol: 'https:', host: 'shop.example' },
    })
    expect(wsBrokerUrl('/api/v1')).toBe('wss://shop.example/ws')
  })

  it('targets API host /ws for absolute API base (Vite dev)', () => {
    expect(wsBrokerUrl('http://localhost:8080/api/v1')).toBe('ws://localhost:8080/ws')
  })
})
