import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'

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

      <form className="space-y-4 rounded-2xl border border-border bg-card p-6 shadow-sm" onSubmit={onSubmit} noValidate>
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
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
            {formError}
          </p>
        ) : null}

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-60"
        >
          {isSubmitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <p className="text-center text-sm text-muted-foreground">
        Already registered?{' '}
        <Link className="font-medium text-primary underline-offset-4 hover:underline" to="/login">
          Sign in
        </Link>
      </p>
    </section>
  )
}
