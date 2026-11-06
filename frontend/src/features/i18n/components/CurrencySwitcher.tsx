import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, ChevronDown, CircleDollarSign } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  APP_CURRENCY_OPTIONS,
  getAppCurrencyOption,
  type AppCurrency,
} from '@/lib/i18n'
import { cn } from '@/lib/utils'

import { useAppCurrency } from '../hooks/useAppCurrency'

type CurrencySwitcherProps = {
  className?: string
  size?: 'sm' | 'default'
}

export function CurrencySwitcher({ className, size = 'sm' }: CurrencySwitcherProps) {
  const { t } = useTranslation('common')
  const { currency, setCurrency } = useAppCurrency()
  const [open, setOpen] = useState(false)
  const current = getAppCurrencyOption(currency)

  const selectCurrency = (next: AppCurrency) => {
    setCurrency(next)
    setOpen(false)
  }

  return (
    <DropdownMenu open={open} onOpenChange={setOpen}>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="outline"
          size={size}
          className={cn('max-w-full gap-1.5 px-2.5 font-normal', className)}
          aria-label={t('currency.switchAria')}
          aria-haspopup="menu"
          data-testid="currency-switcher"
          data-currency={currency}
        >
          <CircleDollarSign className="size-3.5 shrink-0 opacity-70" aria-hidden />
          <span className="truncate">{current.label}</span>
          <ChevronDown className="size-3.5 shrink-0 opacity-60" aria-hidden />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-36">
        <DropdownMenuLabel>{t('currency.label')}</DropdownMenuLabel>
        {APP_CURRENCY_OPTIONS.map((option) => {
          const active = option.code === currency
          return (
            <DropdownMenuItem
              key={option.code}
              data-testid={`currency-option-${option.code}`}
              className={cn(active && 'bg-accent/60')}
              onSelect={() => {
                selectCurrency(option.code)
              }}
            >
              <Check
                className={cn('size-3.5 shrink-0', active ? 'opacity-100' : 'opacity-0')}
                aria-hidden
              />
              <span>{option.label}</span>
            </DropdownMenuItem>
          )
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
