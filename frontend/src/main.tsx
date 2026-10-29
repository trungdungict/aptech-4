import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { ErrorBoundary } from '@/app/ErrorBoundary'
import { AppProviders } from '@/app/providers'
import App from '@/App'
import './index.css'

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('Root element #root not found')
}

createRoot(rootElement).render(
  <StrictMode>
    <ErrorBoundary>
      <AppProviders>
        <App />
      </AppProviders>
    </ErrorBoundary>
  </StrictMode>,
)
