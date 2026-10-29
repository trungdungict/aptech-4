import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { renderWithProviders } from '@/test/render-app'

describe('storefront scaffold', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
  })

  it('renders the landing headline and auth entry points', async () => {
    renderWithProviders(<AppRouter />, { route: '/' })

    expect(
      await screen.findByRole('heading', {
        name: /aptech-4 storefront foundation/i,
      }),
    ).toBeInTheDocument()
    expect(screen.getByText(/scaffold \/ demo content/i)).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /sign in/i }).length).toBeGreaterThan(0)
  })
})
