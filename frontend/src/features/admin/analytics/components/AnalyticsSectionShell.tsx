import type { ReactNode } from 'react'

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
  return (
    <Card
      className={`border-border/80 shadow-sm ${className ?? ''}`.trim()}
      aria-labelledby={`analytics-${slugify(title)}`}
      aria-busy={isLoading || isFetching || undefined}
    >
      <CardHeader className="space-y-1 pb-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle
            id={`analytics-${slugify(title)}`}
            className="text-base font-semibold tracking-tight text-foreground"
          >
            {/* Keep h2 for a11y / E2E heading queries */}
            <h2 className="text-inherit">{title}</h2>
          </CardTitle>
          {isFetching && !isLoading ? (
            <span className="text-xs text-muted-foreground" role="status">
              Updating…
            </span>
          ) : null}
        </div>
        {description ? <p className={adminPageLeadClass}>{description}</p> : null}
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div role="status" aria-live="polite">
            <p className="sr-only">Loading {title}…</p>
            <Skeleton className={`${skeletonHeightClass} w-full rounded-xl`} />
          </div>
        ) : isError && children == null ? (
          <div className={`space-y-3 ${adminAlertErrorClass}`}>
            <p role="alert">
              {error instanceof ApiClientError
                ? error.userMessage
                : `Unable to load ${title.toLowerCase()}.`}
            </p>
            <Button type="button" size="sm" onClick={onRetry}>
              Retry
            </Button>
          </div>
        ) : (
          <div className="space-y-3">
            {isError ? (
              <div className={`space-y-2 ${adminAlertErrorClass}`}>
                <p role="alert">
                  {error instanceof ApiClientError
                    ? error.userMessage
                    : `Unable to refresh ${title.toLowerCase()}.`}
                </p>
                <Button type="button" size="sm" onClick={onRetry}>
                  Retry
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

function slugify(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
}
