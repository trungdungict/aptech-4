import { apiBaseUrl } from '@/lib/env'

/**
 * STOMP broker WebSocket URL.
 * Compose/prod same-origin builds use path-absolute API (`/api/v1`) → derive from
 * `window.location` so no localhost host is baked into the production bundle.
 * Vite dev (absolute API URL) targets the API host's `/ws`.
 */
export function wsBrokerUrl(apiBase: string = apiBaseUrl): string {
  if (typeof window !== 'undefined' && apiBase.startsWith('/') && !apiBase.startsWith('//')) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}/ws`
  }

  try {
    const parsed = new URL(apiBase)
    const protocol = parsed.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${parsed.host}/ws`
  } catch {
    return '/ws'
  }
}
