import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { ApiClientError } from '@/lib/api/api-error'
import { safeInternalPath } from '@/lib/safe-redirect'

import { AuthFormField } from '../components/AuthFormField'
import { useAuth } from '../hooks/useAuth'
import { loginSchema, type LoginFormValues } from '../schemas'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [formError, setFormError] = useState<string | null>(null)
  const nextPath = safeInternalPath(searchParams.get('next'), '/')

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await login(values)
      void navigate(nextPath, { replace: true })
    }
    catch (error) {
      if (error instanceof ApiClientError) {
        setFormError(error.userMessage)
      }
      else {
        setFormError('Unable to sign in. Please try again.')
      }
    }
  })

  return (
    <section className="mx-auto w-full max-w-md space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight text-foreground">Sign in</h1>
        <p className="text-sm text-muted-foreground">
          Use your Aptech-4 customer account. Demo credentials come from seed, not this form.
        </p>
      </div>

      <form className="space-y-4 rounded-2xl border border-border bg-card p-6 shadow-sm" onSubmit={onSubmit} noValidate>
        <AuthFormField
          id="login-email"
          label="Email"
          type="email"
          autoComplete="email"
          disabled={isSubmitting}
          error={errors.email?.message}
          {...register('email')}
        />
        <AuthFormField
          id="login-password"
          label="Password"
          type="password"
          autoComplete="current-password"
          disabled={isSubmitting}
          error={errors.password?.message}
          {...register('password')}
        />

        {formError ? (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
            {formError}
          </p>
        ) : null}

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-60"
        >
          {isSubmitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <p className="text-center text-sm text-muted-foreground">
        No account yet?{' '}
        <Link className="font-medium text-primary underline-offset-4 hover:underline" to="/register">
          Create one
        </Link>
      </p>
    </section>
  )
}
