import type { InputHTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

type AuthFormFieldProps = {
  id: string
  label: string
  error?: string
} & InputHTMLAttributes<HTMLInputElement>

export function AuthFormField({ id, label, error, className, ...inputProps }: AuthFormFieldProps) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-foreground">
        {label}
      </label>
      <input
        id={id}
        className={cn(
          'w-full rounded-lg border border-border bg-card px-3 py-2 text-sm text-foreground shadow-sm outline-none ring-ring placeholder:text-muted-foreground focus-visible:ring-2',
          error && 'border-red-500 focus-visible:ring-red-400',
          className,
        )}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        {...inputProps}
      />
      {error ? (
        <p id={`${id}-error`} className="text-sm text-red-600" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  )
}
