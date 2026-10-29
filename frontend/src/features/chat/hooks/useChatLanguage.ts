import { useCallback, useState } from 'react'

import { CHAT_LANGUAGE_STORAGE_KEY, type ChatLanguage } from '../types'

function readStoredLanguage(): ChatLanguage {
  try {
    const raw = localStorage.getItem(CHAT_LANGUAGE_STORAGE_KEY)
    return raw === 'VI' ? 'VI' : 'EN'
  } catch {
    return 'EN'
  }
}

/** Persists language preference per browser profile (default English). */
export function useChatLanguage(): {
  language: ChatLanguage
  setLanguage: (language: ChatLanguage) => void
} {
  const [language, setLanguageState] = useState<ChatLanguage>(() => readStoredLanguage())

  const setLanguage = useCallback((next: ChatLanguage) => {
    setLanguageState(next)
    try {
      localStorage.setItem(CHAT_LANGUAGE_STORAGE_KEY, next)
    } catch {
      // ignore quota / private mode
    }
  }, [])

  return { language, setLanguage }
}
