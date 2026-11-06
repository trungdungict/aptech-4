import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { listAdminCategories } from '@/features/admin/categories/api/admin-categories-api'
import { adminCategoryQueryKeys } from '@/features/admin/categories/hooks/admin-category-query-keys'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { catalogQueryKeys } from '@/features/catalog/hooks/catalog-query-keys'
import { ApiClientError } from '@/lib/api/api-error'
import { EntityTranslationsEditor } from '@/features/admin/translations'

import {
  adminAlertErrorClass,
  adminAlertSuccessClass,
  adminNativeSelectClass,
  adminPageLeadClass,
  adminPageSectionClass,
  adminPageTitleClass,
} from '../../lib/admin-ui'

import * as productsApi from '../api/admin-products-api'
import { ProductImageUploadField } from '../components/ProductImageUploadField'
import { ProductInventoryForm } from '../components/ProductInventoryForm'
import { useAppLocale } from '@/features/i18n/hooks/useAppLocale'
import { adminProductQueryKeys } from '../hooks/admin-product-query-keys'
import {
  createAdminProductFormSchema,
  type AdminProductFormInput,
  type AdminProductFormValues,
} from '../schemas'

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

type Mode = 'create' | 'edit'

const CANONICAL_LOCALE = 'vi' as const

export function AdminProductFormPage({ mode }: { mode: Mode }) {
  const { t } = useTranslation('admin')
  const { productId = '' } = useParams<{ productId: string }>()
  const isEdit = mode === 'edit'
  const idValid = !isEdit || UUID_RE.test(productId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { locale } = useAppLocale()
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  // Base form always edits canonical Vietnamese text (PUT writes VI base columns), so read it with
  // Accept-Language: vi regardless of UI locale. English lives in the translations editor.
  const productQuery = useQuery({
    queryKey: adminProductQueryKeys.detail(CANONICAL_LOCALE, productId),
    queryFn: ({ signal }) => productsApi.getAdminProduct(productId, signal, CANONICAL_LOCALE),
    enabled: isEdit && idValid,
    retry: false,
  })

  const categoriesQuery = useQuery({
    queryKey: adminCategoryQueryKeys.list(locale),
    queryFn: ({ signal }) => listAdminCategories(signal),
    retry: false,
  })

  const resolver = useMemo(() => zodResolver(createAdminProductFormSchema(t)), [t])
  const form = useForm<AdminProductFormInput, unknown, AdminProductFormValues>({
    resolver,
    defaultValues: {
      sku: '',
      name: '',
      description: '',
      unitPrice: '',
      currency: 'VND',
      categoryId: '',
      imageUrl: null,
    },
  })

  useEffect(() => {
    if (productQuery.data) {
      const p = productQuery.data
      form.reset({
        sku: p.sku,
        name: p.name,
        description: p.description ?? '',
        unitPrice: String(p.unitPrice),
        currency: p.currency || 'VND',
        categoryId: p.categoryId,
        imageUrl: p.imageUrl ?? null,
      })
    }
  }, [productQuery.data, form])

  const saveMutation = useMutation({
    mutationFn: async (values: AdminProductFormValues) => {
      const payload = {
        sku: values.sku.trim(),
        name: values.name.trim(),
        description: values.description?.trim() ? values.description.trim() : null,
        unitPrice: Number(values.unitPrice),
        currency: values.currency?.trim() || 'VND',
        categoryId: values.categoryId,
        imageUrl: values.imageUrl ?? null,
      }
      if (isEdit) {
        return productsApi.updateAdminProduct(productId, payload)
      }
      return productsApi.createAdminProduct(payload)
    },
    onSuccess: async (saved) => {
      setFormError(null)
      setSuccessMessage(isEdit ? t('products.form.updated') : t('products.form.created'))
      await queryClient.invalidateQueries({ queryKey: adminProductQueryKeys.all })
      await queryClient.invalidateQueries({ queryKey: catalogQueryKeys.all })
      if (locale === CANONICAL_LOCALE) {
        queryClient.setQueryData(adminProductQueryKeys.detail(CANONICAL_LOCALE, saved.id), saved)
      }
      void navigate(`/admin/products/${saved.id}/edit`, { replace: true })
    },
    onError: (error) => {
      if (error instanceof ApiClientError) {
        setFormError(error.userMessage)
        return
      }
      setFormError(t('products.form.saveError'))
    },
  })

  const activeCategories =
    categoriesQuery.data?.filter((category) => category.active) ?? []
  const selectedInactive =
    isEdit &&
    productQuery.data &&
    categoriesQuery.data?.find((c) => c.id === productQuery.data.categoryId && !c.active)

  if (isEdit && !idValid) {
    return (
      <Unavailable
        title={t('products.form.invalidLinkTitle')}
        message={t('products.form.invalidLinkMessage')}
        backLabel={t('products.form.backToProducts')}
      />
    )
  }

  if (isEdit && productQuery.isLoading) {
    return (
      <div className="mx-auto max-w-xl space-y-4" role="status" aria-live="polite">
        <p className="sr-only">{t('products.form.loading')}</p>
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    )
  }

  if (isEdit && productQuery.isError) {
    if (productQuery.error instanceof ApiClientError && productQuery.error.status === 404) {
      return (
        <Unavailable
          title={t('products.form.notFoundTitle')}
          message={t('products.form.notFoundMessage')}
          backLabel={t('products.form.backToProducts')}
        />
      )
    }
    return (
      <p className="text-sm text-destructive" role="alert">
        {productQuery.error instanceof ApiClientError
          ? productQuery.error.userMessage
          : t('products.form.loadError')}
      </p>
    )
  }

  return (
    <section className={`mx-auto max-w-xl ${adminPageSectionClass}`}>
      <div className="space-y-2">
        <nav aria-label={t('shell.breadcrumb')} className="text-sm text-muted-foreground">
          <Link to="/admin/products" className="font-medium text-primary hover:underline">
            {t('products.form.breadcrumbProducts')}
          </Link>
          <span aria-hidden="true"> / </span>
          <span className="text-foreground">{isEdit ? t('products.form.breadcrumbEdit') : t('products.form.breadcrumbNew')}</span>
        </nav>
        <h1 className={adminPageTitleClass}>
          {isEdit ? t('products.form.editTitle') : t('products.form.createTitle')}
        </h1>
        <p className={adminPageLeadClass}>
          {isEdit
            ? t('products.form.editLead')
            : t('products.form.createLead')}
        </p>
      </div>

      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>
            <h2 className="text-base font-semibold">{t('products.form.detailsTitle')}</h2>
          </CardTitle>
          <CardDescription>{t('products.form.detailsDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-4"
            noValidate
            onSubmit={form.handleSubmit((values) => {
              setSuccessMessage(null)
              setFormError(null)
              saveMutation.mutate(values)
            })}
          >
            <Field
              id="product-sku"
              label={t('products.form.sku')}
              error={form.formState.errors.sku?.message}
            >
              <Input
                id="product-sku"
                disabled={saveMutation.isPending}
                aria-invalid={Boolean(form.formState.errors.sku)}
                {...form.register('sku')}
              />
            </Field>
            <Field
              id="product-name"
              label={t('products.form.name')}
              error={form.formState.errors.name?.message}
            >
              <Input
                id="product-name"
                disabled={saveMutation.isPending}
                aria-invalid={Boolean(form.formState.errors.name)}
                {...form.register('name')}
              />
            </Field>
            <Field
              id="product-description"
              label={t('products.form.description')}
              error={form.formState.errors.description?.message}
            >
              <Textarea
                id="product-description"
                rows={4}
                disabled={saveMutation.isPending}
                aria-invalid={Boolean(form.formState.errors.description)}
                {...form.register('description')}
              />
            </Field>
            <p className="text-xs text-muted-foreground" data-testid="product-canonical-note">
              {isEdit ? t('products.form.canonicalNote') : t('products.form.createTranslationsHint')}
            </p>

            <Separator />

            <div className="grid gap-4 sm:grid-cols-2">
              <Field
                id="product-price"
                label={t('products.form.unitPrice')}
                error={form.formState.errors.unitPrice?.message}
              >
                <Input
                  id="product-price"
                  inputMode="decimal"
                  disabled={saveMutation.isPending}
                  aria-invalid={Boolean(form.formState.errors.unitPrice)}
                  {...form.register('unitPrice')}
                />
              </Field>
              <Field
                id="product-currency"
                label={t('products.form.currency')}
                error={form.formState.errors.currency?.message}
              >
                <Input
                  id="product-currency"
                  disabled={saveMutation.isPending}
                  aria-invalid={Boolean(form.formState.errors.currency)}
                  {...form.register('currency')}
                />
              </Field>
            </div>
            <Field
              id="product-category"
              label={t('products.form.category')}
              error={form.formState.errors.categoryId?.message}
            >
              <select
                id="product-category"
                className={adminNativeSelectClass}
                disabled={saveMutation.isPending || categoriesQuery.isLoading}
                aria-invalid={Boolean(form.formState.errors.categoryId)}
                {...form.register('categoryId')}
              >
                <option value="">{t('products.form.selectCategory')}</option>
                {selectedInactive ? (
                  <option value={selectedInactive.id}>
                    {t('products.form.inactiveCategory', { name: selectedInactive.name })}
                  </option>
                ) : null}
                {activeCategories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </Field>

            <Controller
              control={form.control}
              name="imageUrl"
              render={({ field }) => (
                <ProductImageUploadField
                  imageUrl={field.value ?? null}
                  productSku={form.watch('sku')}
                  productName={form.watch('name')}
                  onImageUrlChange={field.onChange}
                  disabled={saveMutation.isPending}
                />
              )}
            />

            {categoriesQuery.isError ? (
              <p className="text-sm text-destructive" role="alert">
                {t('products.form.categoriesError')}
              </p>
            ) : null}

            {formError ? (
              <p
                className={adminAlertErrorClass}
                role="alert"
              >
                {formError}
              </p>
            ) : null}
            {successMessage ? (
              <p
                className={adminAlertSuccessClass}
                role="status"
              >
                {successMessage}
              </p>
            ) : null}

            <div className="flex flex-wrap gap-2 pt-2">
              <Button type="submit" disabled={saveMutation.isPending || form.formState.isSubmitting}>
                {saveMutation.isPending
                  ? t('products.form.saving')
                  : isEdit
                    ? t('products.form.save')
                    : t('products.form.create')}
              </Button>
              <Button asChild variant="outline">
                <Link to="/admin/products">{t('products.form.backToList')}</Link>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {isEdit && idValid ? <EntityTranslationsEditor kind="product" entityId={productId} /> : null}

      {isEdit && idValid ? <ProductInventoryForm productId={productId} /> : null}
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
  children: React.ReactNode
}) {
  return (
    <div className="space-y-1.5 text-sm">
      <Label htmlFor={id}>{label}</Label>
      {children}
      {error ? <p className="text-xs text-destructive">{error}</p> : null}
    </div>
  )
}

function Unavailable({
  title,
  message,
  backLabel,
}: {
  title: string
  message: string
  backLabel: string
}) {
  return (
    <Card className="border-dashed shadow-none">
      <CardContent className="flex flex-col items-center gap-3 px-6 py-10 text-center">
        <h1 className="text-xl font-semibold text-foreground">{title}</h1>
        <p className="text-sm text-muted-foreground">{message}</p>
        <Button asChild variant="link">
          <Link to="/admin/products">{backLabel}</Link>
        </Button>
      </CardContent>
    </Card>
  )
}
