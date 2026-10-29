import { QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { BrowserRouter } from 'react-router-dom'

import { AuthQueryBridge } from '@/features/auth/components/AuthQueryBridge'
import { AuthProvider } from '@/features/auth/context/AuthProvider'
import { createAppQueryClient } from '@/lib/query-client'

type AppProvidersProps = {
  children: ReactNode
}

export function AppProviders({ children }: AppProvidersProps) {
  const [queryClient] = useState(() => createAppQueryClient())

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <AuthQueryBridge>{children}</AuthQueryBridge>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
