import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { ErrorBoundary } from '@/app/ErrorBoundary'
import { AppProviders } from '@/app/providers'
import App from '@/App'
import { hydrateActiveCurrencyFromStorage, initI18n } from '@/lib/i18n'
import './index.css'

const rootElement = document.getElementById('root')

if (!rootElement) {
  throw new Error('Root element #root not found')
}

hydrateActiveCurrencyFromStorage()

void initI18n().then(() => {
  createRoot(rootElement).render(
    <StrictMode>
      <ErrorBoundary>
        <AppProviders>
          <App />
        </AppProviders>
      </ErrorBoundary>
    </StrictMode>,
  )
})
