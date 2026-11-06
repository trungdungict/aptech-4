import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, ChevronDown, Globe } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  APP_LOCALE_OPTIONS,
  getAppLocaleOption,
  type AppLocale,
} from '@/lib/i18n'
import { cn } from '@/lib/utils'

import { useAppLocale } from '../hooks/useAppLocale'

type LanguageSwitcherProps = {
  className?: string
  /** Compact control for dense headers. */
  size?: 'sm' | 'default'
}

export function LanguageSwitcher({ className, size = 'sm' }: LanguageSwitcherProps) {
  const { t } = useTranslation('common')
  const { locale, setLocale } = useAppLocale()
  const [open, setOpen] = useState(false)
  const current = getAppLocaleOption(locale)

  const selectLocale = async (next: AppLocale) => {
    await setLocale(next)
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
          aria-label={t('language.switchAria')}
          aria-haspopup="menu"
          data-testid="locale-switcher"
          data-locale={locale}
        >
          <Globe className="size-3.5 shrink-0 opacity-70" aria-hidden />
          <span className="truncate">{current.nativeLabel}</span>
          <ChevronDown className="size-3.5 shrink-0 opacity-60" aria-hidden />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-44">
        <DropdownMenuLabel>{t('language.label')}</DropdownMenuLabel>
        {APP_LOCALE_OPTIONS.map((option) => {
          const active = option.code === locale
          return (
            <DropdownMenuItem
              key={option.code}
              data-testid={`locale-option-${option.code}`}
              className={cn(active && 'bg-accent/60')}
              onSelect={() => {
                void selectLocale(option.code)
              }}
            >
              <Check
                className={cn('size-3.5 shrink-0', active ? 'opacity-100' : 'opacity-0')}
                aria-hidden
              />
              <span>{option.nativeLabel}</span>
            </DropdownMenuItem>
          )
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
