export type ChatLanguage = 'EN' | 'VI'

export type ChatTiming = {
  toolMs?: number | null
  inferenceMs?: number | null
  totalMs?: number | null
  path?: string | null
}

export type ChatResponse = {
  conversationId: string
  reply: string
  language: string
  suggestedPrompts: string[]
  timing?: ChatTiming | null
}

export type ChatHealth = {
  enabled: boolean
  modelConfigured: boolean
  model: string
  status: string
  detail: string
}

export type ChatMessage = {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  interrupted?: boolean
}

export const CHAT_LANGUAGE_STORAGE_KEY = 'aptech4.chat.language'
