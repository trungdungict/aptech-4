import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { listAdminCategories } from '@/features/admin/categories/api/admin-categories-api'
import { adminCategoryQueryKeys } from '@/features/admin/categories/hooks/admin-category-query-keys'
import { catalogQueryKeys } from '@/features/catalog/hooks/catalog-query-keys'
import { ApiClientError } from '@/lib/api/api-error'

import * as productsApi from '../api/admin-products-api'
import { adminProductQueryKeys } from '../hooks/admin-product-query-keys'
import { adminProductFormSchema, type AdminProductFormValues } from '../schemas'

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

  const form = useForm<AdminProductFormValues>({
    resolver: zodResolver(adminProductFormSchema),
    defaultValues: {
      sku: '',
      name: '',
      description: '',
      unitPrice: '',
      currency: 'VND',
      categoryId: '',
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
      <p className="text-sm text-muted-foreground" role="status">
        Loading product…
      </p>
    )
  }

  if (isEdit && productQuery.isError) {
    if (productQuery.error instanceof ApiClientError && productQuery.error.status === 404) {
      return (
        <Unavailable title="Product not found" message="This product does not exist or was removed." />
      )
    }
    return (
      <p className="text-sm text-red-700" role="alert">
        {productQuery.error instanceof ApiClientError
          ? productQuery.error.userMessage
          : 'Unable to load product.'}
      </p>
    )
  }

  return (
    <section className="mx-auto max-w-xl space-y-6">
      <div className="space-y-1">
        <p className="text-sm text-muted-foreground">
          <Link to="/admin/products" className="text-primary underline-offset-4 hover:underline">
            Products
          </Link>
          <span aria-hidden="true"> / </span>
          {isEdit ? 'Edit' : 'New'}
        </p>
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
          {isEdit ? 'Edit product' : 'Create product'}
        </h1>
        <p className="text-sm text-muted-foreground">
          Fields match the admin product API. Inventory quantities are not editable here.
        </p>
      </div>

      <form
        className="space-y-4 rounded-2xl border border-border bg-card p-5"
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
          <input
            id="product-sku"
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
            disabled={saveMutation.isPending}
            {...form.register('sku')}
          />
        </Field>
        <Field
          id="product-name"
          label="Name"
          error={form.formState.errors.name?.message}
        >
          <input
            id="product-name"
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
            disabled={saveMutation.isPending}
            {...form.register('name')}
          />
        </Field>
        <Field
          id="product-description"
          label="Description"
          error={form.formState.errors.description?.message}
        >
          <textarea
            id="product-description"
            rows={4}
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
            disabled={saveMutation.isPending}
            {...form.register('description')}
          />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            id="product-price"
            label="Unit price"
            error={form.formState.errors.unitPrice?.message}
          >
            <input
              id="product-price"
              inputMode="decimal"
              className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              disabled={saveMutation.isPending}
              {...form.register('unitPrice')}
            />
          </Field>
          <Field
            id="product-currency"
            label="Currency"
            error={form.formState.errors.currency?.message}
          >
            <input
              id="product-currency"
              className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
              disabled={saveMutation.isPending}
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
            className="w-full rounded-lg border border-border bg-background px-3 py-2 text-sm"
            disabled={saveMutation.isPending || categoriesQuery.isLoading}
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

        {categoriesQuery.isError ? (
          <p className="text-sm text-red-700" role="alert">
            Unable to load categories for the form.
          </p>
        ) : null}

        {formError ? (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
            {formError}
          </p>
        ) : null}
        {successMessage ? (
          <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-900" role="status">
            {successMessage}
          </p>
        ) : null}

        <div className="flex flex-wrap gap-2">
          <button
            type="submit"
            disabled={saveMutation.isPending}
            className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
          >
            {saveMutation.isPending ? 'Saving…' : isEdit ? 'Save changes' : 'Create product'}
          </button>
          <Link
            to="/admin/products"
            className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-muted"
          >
            Back to list
          </Link>
        </div>
      </form>
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
    <div className="space-y-1 text-sm">
      <label className="block text-muted-foreground" htmlFor={id}>
        {label}
      </label>
      {children}
      {error ? <p className="text-xs text-red-700">{error}</p> : null}
    </div>
  )
}

function Unavailable({ title, message }: { title: string; message: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-border bg-card px-6 py-10 text-center">
      <h1 className="text-xl font-semibold text-foreground">{title}</h1>
      <p className="mt-2 text-sm text-muted-foreground">{message}</p>
      <Link
        to="/admin/products"
        className="mt-4 inline-flex text-sm font-medium text-primary underline-offset-4 hover:underline"
      >
        Back to products
      </Link>
    </div>
  )
}
