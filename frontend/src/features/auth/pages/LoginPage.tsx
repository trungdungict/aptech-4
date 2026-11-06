import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ApiClientError } from '@/lib/api/api-error'
import { safeInternalPath } from '@/lib/safe-redirect'

import { AuthFormField } from '../components/AuthFormField'
import { useAuth } from '../hooks/useAuth'
import { createLoginSchema, type LoginFormValues } from '../schemas'

export function LoginPage() {
  const { t } = useTranslation('auth')
  const { t: tv } = useTranslation('validation')
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [formError, setFormError] = useState<string | null>(null)
  const nextPath = safeInternalPath(searchParams.get('next'), '/')

  const resolver = useMemo(() => zodResolver(createLoginSchema(tv)), [tv])

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver,
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
        setFormError(t('login.unknownError'))
      }
    }
  })

  return (
    <section className="mx-auto w-full max-w-md space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight text-foreground">{t('login.title')}</h1>
        <p className="text-sm text-muted-foreground">
          {t('login.subtitle')}
        </p>
      </div>

      <Card className="gap-0 py-0 shadow-sm">
        <CardContent className="p-6">
          <form className="space-y-4" onSubmit={onSubmit} noValidate>
            <AuthFormField
              id="login-email"
              label={t('login.email')}
              type="email"
              autoComplete="email"
              disabled={isSubmitting}
              error={errors.email?.message}
              {...register('email')}
            />
            <AuthFormField
              id="login-password"
              label={t('login.password')}
              type="password"
              autoComplete="current-password"
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
              {isSubmitting ? t('login.submitting') : t('login.submit')}
            </Button>
          </form>
        </CardContent>
      </Card>

      <p className="text-center text-sm text-muted-foreground">
        {t('login.noAccount')}{' '}
        <Link className="font-medium text-primary underline-offset-4 hover:underline" to="/register">
          {t('login.createOne')}
        </Link>
      </p>
    </section>
  )
}
