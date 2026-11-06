import { QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { I18nextProvider } from 'react-i18next'
import { BrowserRouter } from 'react-router-dom'

import { AuthQueryBridge } from '@/features/auth/components/AuthQueryBridge'
import { AuthProvider } from '@/features/auth/context/AuthProvider'
import { i18n } from '@/lib/i18n'
import { createAppQueryClient } from '@/lib/query-client'

type AppProvidersProps = {
  children: ReactNode
}

export function AppProviders({ children }: AppProvidersProps) {
  const [queryClient] = useState(() => createAppQueryClient())

  return (
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <AuthQueryBridge>{children}</AuthQueryBridge>
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </I18nextProvider>
  )
}
