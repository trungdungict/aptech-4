import type { FormEvent, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'

import { adminAlertErrorClass, adminAlertSuccessClass } from '../../lib/admin-ui'

type TranslationsEditorFrameProps = {
  title: string
  description?: string
  testId: string
  isLoading: boolean
  loadError: string | null
  onReload: () => void
  saving: boolean
  saveError: string | null
  saved: boolean
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  children: ReactNode
}

/** Shared load / error / save chrome for every Admin translation editor. */
export function TranslationsEditorFrame({
  title,
  description,
  testId,
  isLoading,
  loadError,
  onReload,
  saving,
  saveError,
  saved,
  onSubmit,
  children,
}: TranslationsEditorFrameProps) {
  const { t } = useTranslation('admin')

  return (
    <Card className="shadow-sm" data-testid={testId}>
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">{title}</h2>
        </CardTitle>
        <CardDescription>{description ?? t('translations.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div role="status" aria-live="polite" className="space-y-3">
            <p className="sr-only">{t('translations.loading')}</p>
            <Skeleton className="h-20 w-full" />
          </div>
        ) : loadError ? (
          <div className={`space-y-3 ${adminAlertErrorClass}`}>
            <p role="alert">{loadError}</p>
            <Button type="button" size="sm" onClick={onReload}>
              {t('translations.reload')}
            </Button>
          </div>
        ) : (
          <form className="space-y-4" noValidate onSubmit={onSubmit}>
            <div className="grid gap-6 md:grid-cols-2">{children}</div>
            {saveError ? (
              <p className={adminAlertErrorClass} role="alert">
                {saveError}
              </p>
            ) : null}
            {saved ? (
              <p className={adminAlertSuccessClass} role="status">
                {t('translations.saved')}
              </p>
            ) : null}
            <Button type="submit" disabled={saving}>
              {saving ? t('translations.saving') : t('translations.save')}
            </Button>
          </form>
        )}
      </CardContent>
    </Card>
  )
}

/** One locale column (fieldset) inside an editor. */
export function TranslationLocaleGroup({
  legend,
  children,
}: {
  legend: string
  children: ReactNode
}) {
  return (
    <fieldset className="space-y-3 rounded-lg border border-border p-3">
      <legend className="px-1 text-sm font-medium text-foreground">{legend}</legend>
      {children}
    </fieldset>
  )
}

export function TranslationField({
  id,
  label,
  error,
  children,
}: {
  id: string
  label: string
  error?: string
  children: ReactNode
}) {
  return (
    <div className="space-y-1.5 text-sm">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  )
}
