import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { clearSession } from '@/features/auth/session/session-coordinator'
import {
  APP_LOCALE_OPTIONS,
  APP_LOCALE_STORAGE_KEY,
  changeAppLocale,
  getActiveLocale,
} from '@/lib/i18n'
import { renderWithProviders } from '@/test/render-app'

import { LanguageSwitcher } from './LanguageSwitcher'

describe('LanguageSwitcher', () => {
  beforeEach(async () => {
    clearSession()
    localStorage.removeItem(APP_LOCALE_STORAGE_KEY)
    await changeAppLocale('vi')
  })

  afterEach(() => {
    clearSession()
    localStorage.removeItem(APP_LOCALE_STORAGE_KEY)
  })

  it('shows the current locale label on the compact trigger', async () => {
    renderWithProviders(<LanguageSwitcher />)

    const trigger = await screen.findByTestId('locale-switcher')
    expect(trigger).toHaveAttribute('aria-label', 'Chọn ngôn ngữ')
    expect(trigger).toHaveAttribute('data-locale', 'vi')
    expect(trigger).toHaveTextContent('Tiếng Việt')
    expect(trigger.querySelector('svg')).toBeTruthy()
  })

  it('lists supported locales and selects English without hardcoding handlers', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LanguageSwitcher />)

    await user.click(await screen.findByTestId('locale-switcher'))

    const menu = await screen.findByRole('menu')
    for (const option of APP_LOCALE_OPTIONS) {
      expect(within(menu).getByTestId(`locale-option-${option.code}`)).toHaveTextContent(
        option.nativeLabel,
      )
    }

    await user.click(within(menu).getByTestId('locale-option-en'))

    await waitFor(() => {
      expect(screen.getByTestId('locale-switcher')).toHaveAttribute('data-locale', 'en')
    })
    expect(screen.getByTestId('locale-switcher')).toHaveTextContent('English')
    expect(getActiveLocale()).toBe('en')
    expect(localStorage.getItem(APP_LOCALE_STORAGE_KEY)).toBe('en')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('closes on Escape without changing locale', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LanguageSwitcher />)

    await user.click(await screen.findByTestId('locale-switcher'))
    expect(await screen.findByRole('menu')).toBeInTheDocument()

    await user.keyboard('{Escape}')
    await waitFor(() => {
      expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    })
    expect(getActiveLocale()).toBe('vi')
    expect(screen.getByTestId('locale-switcher')).toHaveTextContent('Tiếng Việt')
  })
})
