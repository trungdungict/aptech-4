import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { clearSession } from '@/features/auth/session/session-coordinator'
import {
  APP_CURRENCY_OPTIONS,
  APP_CURRENCY_STORAGE_KEY,
  APP_LOCALE_STORAGE_KEY,
  changeAppLocale,
  getActiveCurrency,
  getActiveLocale,
  setActiveCurrency,
} from '@/lib/i18n'
import { renderWithProviders } from '@/test/render-app'

import { CurrencySwitcher } from './CurrencySwitcher'
import { LanguageSwitcher } from './LanguageSwitcher'

describe('CurrencySwitcher', () => {
  beforeEach(async () => {
    clearSession()
    localStorage.removeItem(APP_CURRENCY_STORAGE_KEY)
    localStorage.removeItem(APP_LOCALE_STORAGE_KEY)
    setActiveCurrency('USD')
    await changeAppLocale('en')
  })

  afterEach(() => {
    clearSession()
    localStorage.removeItem(APP_CURRENCY_STORAGE_KEY)
    localStorage.removeItem(APP_LOCALE_STORAGE_KEY)
  })

  it('shows the current currency on the compact trigger', async () => {
    renderWithProviders(<CurrencySwitcher />)

    const trigger = await screen.findByTestId('currency-switcher')
    expect(trigger).toHaveAttribute('aria-label', 'Select currency')
    expect(trigger).toHaveAttribute('data-currency', 'USD')
    expect(trigger).toHaveTextContent('USD')
  })

  it('lists currencies and persists VND without changing locale', async () => {
    const user = userEvent.setup()
    renderWithProviders(
      <>
        <LanguageSwitcher />
        <CurrencySwitcher />
      </>,
    )

    expect(getActiveLocale()).toBe('en')
    await user.click(await screen.findByTestId('currency-switcher'))

    const menu = await screen.findByRole('menu')
    for (const option of APP_CURRENCY_OPTIONS) {
      expect(within(menu).getByTestId(`currency-option-${option.code}`)).toHaveTextContent(
        option.label,
      )
    }

    await user.click(within(menu).getByTestId('currency-option-VND'))

    await waitFor(() => {
      expect(screen.getByTestId('currency-switcher')).toHaveAttribute('data-currency', 'VND')
    })
    expect(getActiveCurrency()).toBe('VND')
    expect(localStorage.getItem(APP_CURRENCY_STORAGE_KEY)).toBe('VND')
    expect(getActiveLocale()).toBe('en')
    expect(localStorage.getItem(APP_LOCALE_STORAGE_KEY)).toBe('en')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('does not change currency when language changes', async () => {
    const user = userEvent.setup()
    setActiveCurrency('VND')
    renderWithProviders(
      <>
        <LanguageSwitcher />
        <CurrencySwitcher />
      </>,
    )

    expect(screen.getByTestId('currency-switcher')).toHaveAttribute('data-currency', 'VND')
    await user.click(await screen.findByTestId('locale-switcher'))
    await user.click(within(await screen.findByRole('menu')).getByTestId('locale-option-vi'))

    await waitFor(() => {
      expect(screen.getByTestId('locale-switcher')).toHaveAttribute('data-locale', 'vi')
    })
    expect(getActiveCurrency()).toBe('VND')
    expect(localStorage.getItem(APP_CURRENCY_STORAGE_KEY)).toBe('VND')
  })
})
