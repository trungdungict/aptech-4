import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ApiClientError } from '@/lib/api/api-error'

import { AuthFormField } from '../components/AuthFormField'
import { useAuth } from '../hooks/useAuth'
import { registerSchema, type RegisterFormValues } from '../schemas'

export function RegisterPage() {
  const { register: registerAccount } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { email: '', fullName: '', password: '' },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      // Backend issues CUSTOMER session on 201 — no client role field.
      await registerAccount(values)
      void navigate('/', { replace: true })
    }
    catch (error) {
      if (error instanceof ApiClientError) {
        setFormError(error.userMessage)
      }
      else {
        setFormError('Unable to register. Please try again.')
      }
    }
  })

  return (
    <section className="mx-auto w-full max-w-md space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight text-foreground">Create account</h1>
        <p className="text-sm text-muted-foreground">
          Registers a customer account only. Admin privileges cannot be selected here.
        </p>
      </div>

      <Card className="gap-0 py-0 shadow-sm">
        <CardContent className="p-6">
          <form className="space-y-4" onSubmit={onSubmit} noValidate>
            <AuthFormField
              id="register-full-name"
              label="Full name"
              type="text"
              autoComplete="name"
              disabled={isSubmitting}
              error={errors.fullName?.message}
              {...register('fullName')}
            />
            <AuthFormField
              id="register-email"
              label="Email"
              type="email"
              autoComplete="email"
              disabled={isSubmitting}
              error={errors.email?.message}
              {...register('email')}
            />
            <AuthFormField
              id="register-password"
              label="Password"
              type="password"
              autoComplete="new-password"
              disabled={isSubmitting}
              error={errors.password?.message}
              {...register('password')}
            />

            {formError ? (
              <p className="rounded-lg border border-destructive/20 bg-destructive/10 px-3 py-2 text-sm text-destructive" role="alert">
                {formError}
              </p>
            ) : null}

            <Button type="submit" className="w-full" disabled={isSubmitting}>
              {isSubmitting ? 'Creating account…' : 'Create account'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <p className="text-center text-sm text-muted-foreground">
        Already registered?{' '}
        <Link className="font-medium text-primary underline-offset-4 hover:underline" to="/login">
          Sign in
        </Link>
      </p>
    </section>
  )
}
