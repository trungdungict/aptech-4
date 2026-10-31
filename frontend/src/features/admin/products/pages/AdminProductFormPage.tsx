import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
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
import { adminProductQueryKeys } from '../hooks/admin-product-query-keys'
import {
  adminProductFormSchema,
  type AdminProductFormInput,
  type AdminProductFormValues,
} from '../schemas'

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

type Mode = 'create' | 'edit'

export function AdminProductFormPage({ mode }: { mode: Mode }) {
  const { productId = '' } = useParams<{ productId: string }>()
  const isEdit = mode === 'edit'
  const idValid = !isEdit || UUID_RE.test(productId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const productQuery = useQuery({
    queryKey: adminProductQueryKeys.detail(productId),
    queryFn: ({ signal }) => productsApi.getAdminProduct(productId, signal),
    enabled: isEdit && idValid,
    retry: false,
  })

  const categoriesQuery = useQuery({
    queryKey: adminCategoryQueryKeys.list(),
    queryFn: ({ signal }) => listAdminCategories(signal),
    retry: false,
  })

  const form = useForm<AdminProductFormInput, unknown, AdminProductFormValues>({
    resolver: zodResolver(adminProductFormSchema),
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
      setSuccessMessage(isEdit ? 'Product updated.' : 'Product created.')
      await queryClient.invalidateQueries({ queryKey: adminProductQueryKeys.all })
      await queryClient.invalidateQueries({ queryKey: catalogQueryKeys.all })
      queryClient.setQueryData(adminProductQueryKeys.detail(saved.id), saved)
      void navigate(`/admin/products/${saved.id}/edit`, { replace: true })
    },
    onError: (error) => {
      if (error instanceof ApiClientError) {
        setFormError(error.userMessage)
        return
      }
      setFormError('Unable to save product.')
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
      <Unavailable title="Invalid product link" message="The product id in the URL is not valid." />
    )
  }

  if (isEdit && productQuery.isLoading) {
    return (
      <div className="mx-auto max-w-xl space-y-4" role="status" aria-live="polite">
        <p className="sr-only">Loading product…</p>
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    )
  }

  if (isEdit && productQuery.isError) {
    if (productQuery.error instanceof ApiClientError && productQuery.error.status === 404) {
      return (
        <Unavailable title="Product not found" message="This product does not exist or was removed." />
      )
    }
    return (
      <p className="text-sm text-destructive" role="alert">
        {productQuery.error instanceof ApiClientError
          ? productQuery.error.userMessage
          : 'Unable to load product.'}
      </p>
    )
  }

  return (
    <section className={`mx-auto max-w-xl ${adminPageSectionClass}`}>
      <div className="space-y-2">
        <nav aria-label="Breadcrumb" className="text-sm text-muted-foreground">
          <Link to="/admin/products" className="font-medium text-primary hover:underline">
            Products
          </Link>
          <span aria-hidden="true"> / </span>
          <span className="text-foreground">{isEdit ? 'Edit' : 'New'}</span>
        </nav>
        <h1 className={adminPageTitleClass}>
          {isEdit ? 'Edit product' : 'Create product'}
        </h1>
        <p className={adminPageLeadClass}>
          {isEdit
            ? 'Update catalog fields below. Stock is managed in the Inventory section.'
            : 'Create the product first, then set stock quantity on the edit page.'}
        </p>
      </div>

      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>
            <h2 className="text-base font-semibold">Product details</h2>
          </CardTitle>
          <CardDescription>SKU, pricing, and category assignment.</CardDescription>
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
              label="SKU"
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
              label="Name"
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
              label="Description"
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

            <Separator />

            <div className="grid gap-4 sm:grid-cols-2">
              <Field
                id="product-price"
                label="Unit price"
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
                label="Currency"
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
              label="Category"
              error={form.formState.errors.categoryId?.message}
            >
              <select
                id="product-category"
                className={adminNativeSelectClass}
                disabled={saveMutation.isPending || categoriesQuery.isLoading}
                aria-invalid={Boolean(form.formState.errors.categoryId)}
                {...form.register('categoryId')}
              >
                <option value="">Select a category</option>
                {selectedInactive ? (
                  <option value={selectedInactive.id}>
                    {selectedInactive.name} (inactive — reassignment required)
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
                  onImageUrlChange={field.onChange}
                  disabled={saveMutation.isPending}
                />
              )}
            />

            {categoriesQuery.isError ? (
              <p className="text-sm text-destructive" role="alert">
                Unable to load categories for the form.
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
                {saveMutation.isPending ? 'Saving…' : isEdit ? 'Save changes' : 'Create product'}
              </Button>
              <Button asChild variant="outline">
                <Link to="/admin/products">Back to list</Link>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

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

function Unavailable({ title, message }: { title: string; message: string }) {
  return (
    <Card className="border-dashed shadow-none">
      <CardContent className="flex flex-col items-center gap-3 px-6 py-10 text-center">
        <h1 className="text-xl font-semibold text-foreground">{title}</h1>
        <p className="text-sm text-muted-foreground">{message}</p>
        <Button asChild variant="link">
          <Link to="/admin/products">Back to products</Link>
        </Button>
      </CardContent>
    </Card>
  )
}
