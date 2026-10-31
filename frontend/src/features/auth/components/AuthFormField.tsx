import type { InputHTMLAttributes } from 'react'

import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { cn } from '@/lib/utils'

type AuthFormFieldProps = {
  id: string
  label: string
  error?: string
} & InputHTMLAttributes<HTMLInputElement>

export function AuthFormField({ id, label, error, className, ...inputProps }: AuthFormFieldProps) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        className={cn(error && 'border-destructive focus-visible:ring-destructive/30', className)}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : undefined}
        {...inputProps}
      />
      {error ? (
        <p id={`${id}-error`} className="text-sm text-destructive" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  )
}
