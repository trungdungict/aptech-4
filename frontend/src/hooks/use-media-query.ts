import { useEffect, useState } from 'react'

/**
 * Subscribe to a CSS media query.
 * Defaults to {@code defaultMatches} when `window.matchMedia` is unavailable
 * (e.g. jsdom unit tests) so layout shells can keep a stable desktop tree.
 */
export function useMediaQuery(query: string, defaultMatches = false): boolean {
  const [matches, setMatches] = useState(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return defaultMatches
    }
    return window.matchMedia(query).matches
  })

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return
    }
    const media = window.matchMedia(query)
    const onChange = () => setMatches(media.matches)
    onChange()
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [query])

  return matches
}
