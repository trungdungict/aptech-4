import { useId, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiClientError } from '@/lib/api/api-error'

import { adminAlertErrorClass, adminPageLeadClass } from '../../lib/admin-ui'

type AnalyticsSectionShellProps = {
  title: string
  description?: string
  isLoading: boolean
  isFetching?: boolean
  isError: boolean
  error: unknown
  onRetry: () => void
  skeletonHeightClass?: string
  children: ReactNode
  className?: string
}

export function AnalyticsSectionShell({
  title,
  description,
  isLoading,
  isFetching = false,
  isError,
  error,
  onRetry,
  skeletonHeightClass = 'h-40',
  children,
  className,
}: AnalyticsSectionShellProps) {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const headingId = useId()
  const lowerTitle = title.toLowerCase()
  return (
    <Card
      className={`border-border/80 shadow-sm ${className ?? ''}`.trim()}
      aria-labelledby={headingId}
      aria-busy={isLoading || isFetching || undefined}
    >
      <CardHeader className="space-y-1 pb-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle
            id={headingId}
            className="text-base font-semibold tracking-tight text-foreground"
          >
            {/* Keep h2 for a11y / E2E heading queries */}
            <h2 className="text-inherit">{title}</h2>
          </CardTitle>
          {isFetching && !isLoading ? (
            <span className="text-xs text-muted-foreground" role="status">
              {t('analytics.shell.updating')}
            </span>
          ) : null}
        </div>
        {description ? <p className={adminPageLeadClass}>{description}</p> : null}
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div role="status" aria-live="polite">
            <p className="sr-only">{t('analytics.shell.loading', { title })}</p>
            <Skeleton className={`${skeletonHeightClass} w-full rounded-xl`} />
          </div>
        ) : isError && children == null ? (
          <div className={`space-y-3 ${adminAlertErrorClass}`}>
            <p role="alert">
              {error instanceof ApiClientError
                ? error.userMessage
                : t('analytics.shell.loadError', { title: lowerTitle })}
            </p>
            <Button type="button" size="sm" onClick={onRetry}>
              {tc('actions.retry')}
            </Button>
          </div>
        ) : (
          <div className="space-y-3">
            {isError ? (
              <div className={`space-y-2 ${adminAlertErrorClass}`}>
                <p role="alert">
                  {error instanceof ApiClientError
                    ? error.userMessage
                    : t('analytics.shell.refreshError', { title: lowerTitle })}
                </p>
                <Button type="button" size="sm" onClick={onRetry}>
                  {tc('actions.retry')}
                </Button>
              </div>
            ) : null}
            {children}
          </div>
        )}
      </CardContent>
    </Card>
  )
}
