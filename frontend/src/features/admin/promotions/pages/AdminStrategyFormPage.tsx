import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiClientError } from '@/lib/api/api-error'
import { NameTranslationsEditor } from '@/features/admin/translations'

import {
  adminAlertErrorClass,
  adminAlertSuccessClass,
  adminNativeSelectClass,
  adminPageLeadClass,
  adminPageSectionClass,
  adminPageTitleClass,
} from '../../lib/admin-ui'
import * as api from '../api/admin-promotions-api'
import { adminPromotionQueryKeys } from '../hooks/admin-promotion-query-keys'
import {
  createAdminStrategyFormSchema,
  type AdminStrategyFormInput,
  type AdminStrategyFormValues,
} from '../schemas'
import { STRATEGY_TYPES } from '../types'

/** Admin base PUT writes the canonical VI column, so edit forms always read VI text. */
const CANONICAL_LOCALE = 'vi' as const

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

type Mode = 'create' | 'edit'

export function AdminStrategyFormPage({ mode }: { mode: Mode }) {
  const { strategyId = '' } = useParams<{ strategyId: string }>()
  const isEdit = mode === 'edit'
  const idValid = !isEdit || UUID_RE.test(strategyId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { t } = useTranslation('admin')
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const strategyQuery = useQuery({
    queryKey: adminPromotionQueryKeys.strategy(CANONICAL_LOCALE, strategyId),
    queryFn: ({ signal }) => api.getAdminStrategy(strategyId, signal, CANONICAL_LOCALE),
    enabled: isEdit && idValid,
    retry: false,
  })

  const resolver = useMemo(() => zodResolver(createAdminStrategyFormSchema(t)), [t])

  const form = useForm<AdminStrategyFormInput, unknown, AdminStrategyFormValues>({
    resolver,
    defaultValues: {
      name: '',
      code: '',
      type: 'BANNER_ONLY',
      discountPercent: '',
      discountAmount: '',
      maxDiscountAmount: '',
      minOrderAmount: '',
      minQuantity: '',
      stackable: false,
      priority: '100',
      active: true,
    },
  })

  useEffect(() => {
    if (isEdit && strategyQuery.data) {
      const s = strategyQuery.data
      form.reset({
        name: s.name,
        code: s.code ?? '',
        type: s.type,
        discountPercent: s.discountPercent == null ? '' : String(s.discountPercent),
        discountAmount: s.discountAmount == null ? '' : String(s.discountAmount),
        maxDiscountAmount: s.maxDiscountAmount == null ? '' : String(s.maxDiscountAmount),
        minOrderAmount: s.minOrderAmount == null ? '' : String(s.minOrderAmount),
        minQuantity: s.minQuantity == null ? '' : String(s.minQuantity),
        stackable: s.stackable,
        priority: String(s.priority),
        active: s.active,
      })
    }
  }, [form, isEdit, strategyQuery.data])

  const saveMutation = useMutation({
    mutationFn: async (values: AdminStrategyFormValues) => {
      const payload = {
        name: values.name.trim(),
        code: values.code?.trim() ? values.code.trim() : null,
        type: values.type,
        discountPercent: values.discountPercent,
        discountAmount: values.discountAmount,
        maxDiscountAmount: values.maxDiscountAmount,
        minOrderAmount: values.minOrderAmount,
        minQuantity: values.minQuantity == null ? null : Math.trunc(values.minQuantity),
        stackable: values.stackable,
        priority: values.priority,
        active: values.active,
      }
      if (isEdit) {
        return api.updateAdminStrategy(strategyId, payload)
      }
      return api.createAdminStrategy(payload)
    },
    onSuccess: async (saved) => {
      setFormError(null)
      setSuccessMessage(isEdit ? t('promotions.strategies.form.updated') : t('promotions.strategies.form.created'))
      await queryClient.invalidateQueries({ queryKey: adminPromotionQueryKeys.all })
      void navigate(`/admin/promotions/strategies/${saved.id}/edit`, { replace: true })
    },
    onError: (error) => {
      setFormError(error instanceof ApiClientError ? error.userMessage : t('promotions.strategies.form.saveError'))
    },
  })

  if (isEdit && !idValid) {
    return <p className={adminAlertErrorClass}>{t('promotions.strategies.form.invalidId')}</p>
  }

  if (isEdit && strategyQuery.isLoading) {
    return (
      <div className="space-y-4" role="status">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    )
  }

  if (isEdit && strategyQuery.isError) {
    return (
      <p className={adminAlertErrorClass} role="alert">
        {strategyQuery.error instanceof ApiClientError
          ? strategyQuery.error.userMessage
          : t('promotions.strategies.form.notFound')}
      </p>
    )
  }

  const type = form.watch('type')

  return (
    <section className={`mx-auto max-w-2xl ${adminPageSectionClass}`}>
      <div className="space-y-2">
        <nav aria-label={t('shell.breadcrumb')} className="text-sm text-muted-foreground">
          <Link to="/admin/promotions/strategies" className="font-medium text-primary hover:underline">
            {t('promotions.breadcrumb.strategies')}
          </Link>
          <span aria-hidden="true"> / </span>
          <span className="text-foreground">{isEdit ? t('promotions.breadcrumb.edit') : t('promotions.breadcrumb.new')}</span>
        </nav>
        <h1 className={adminPageTitleClass}>{isEdit ? t('promotions.strategies.form.editTitle') : t('promotions.strategies.form.createTitle')}</h1>
        <p className={adminPageLeadClass}>
          {t('promotions.strategies.form.lead')}
        </p>
      </div>

      {formError ? (
        <p className={adminAlertErrorClass} role="alert">
          {formError}
        </p>
      ) : null}
      {successMessage ? <p className={adminAlertSuccessClass}>{successMessage}</p> : null}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">{t('promotions.strategies.form.configTitle')}</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-4"
            onSubmit={form.handleSubmit((values) => saveMutation.mutate(values))}
            noValidate
          >
            <Field id="strategy-name" label={t('promotions.strategies.form.name')} error={form.formState.errors.name?.message}>
              <Input id="strategy-name" {...form.register('name')} />
            </Field>
            <p className="-mt-2 text-xs text-muted-foreground" data-testid="strategy-canonical-note">
              {isEdit
                ? t('promotions.strategies.form.canonicalNote')
                : t('promotions.strategies.form.createHint')}
            </p>
            <Field id="strategy-code" label={t('promotions.strategies.form.code')} error={form.formState.errors.code?.message}>
              <Input id="strategy-code" {...form.register('code')} />
            </Field>
            <Field id="strategy-type" label={t('promotions.strategies.form.type')} error={form.formState.errors.type?.message}>
              <select id="strategy-type" className={adminNativeSelectClass} {...form.register('type')}>
                {STRATEGY_TYPES.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </Field>
            {type !== 'BANNER_ONLY' ? (
              <div className="grid gap-4 sm:grid-cols-2">
                <Field id="discount-percent" label={t('promotions.strategies.form.discountPercent')} error={form.formState.errors.discountPercent?.message}>
                  <Input id="discount-percent" type="number" step="0.01" {...form.register('discountPercent')} />
                </Field>
                <Field id="discount-amount" label={t('promotions.strategies.form.discountAmount')} error={form.formState.errors.discountAmount?.message}>
                  <Input id="discount-amount" type="number" step="0.01" {...form.register('discountAmount')} />
                </Field>
                <Field id="max-discount" label={t('promotions.strategies.form.maxDiscount')} error={form.formState.errors.maxDiscountAmount?.message}>
                  <Input id="max-discount" type="number" step="0.01" {...form.register('maxDiscountAmount')} />
                </Field>
                <Field id="min-order" label={t('promotions.strategies.form.minOrder')} error={form.formState.errors.minOrderAmount?.message}>
                  <Input id="min-order" type="number" step="0.01" {...form.register('minOrderAmount')} />
                </Field>
                <Field id="min-qty" label={t('promotions.strategies.form.minQuantity')} error={form.formState.errors.minQuantity?.message}>
                  <Input id="min-qty" type="number" step="1" {...form.register('minQuantity')} />
                </Field>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                {t('promotions.strategies.form.bannerNote')}
              </p>
            )}
            <div className="grid gap-4 sm:grid-cols-3">
              <Field id="strategy-priority" label={t('promotions.strategies.form.priority')} error={form.formState.errors.priority?.message}>
                <Input id="strategy-priority" type="number" {...form.register('priority')} />
              </Field>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" {...form.register('stackable')} />
                {t('promotions.strategies.form.stackable')}
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" {...form.register('active')} />
                {t('promotions.strategies.form.active')}
              </label>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button type="submit" disabled={saveMutation.isPending}>
                {saveMutation.isPending ? t('promotions.strategies.form.saving') : t('promotions.strategies.form.save')}
              </Button>
              <Button asChild type="button" variant="outline">
                <Link to="/admin/promotions/strategies">{t('promotions.strategies.form.cancel')}</Link>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {isEdit ? <NameTranslationsEditor kind="strategy" entityId={strategyId} /> : null}
    </section>
  )
}

function Field({
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
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? (
        <p className="text-xs text-destructive" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  )
}
