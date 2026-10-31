import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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

import * as categoriesApi from '../api/admin-categories-api'
import { adminCategoryQueryKeys } from '../hooks/admin-category-query-keys'
import { adminCategoryFormSchema, type AdminCategoryFormValues } from '../schemas'

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

type Mode = 'create' | 'edit'

export function AdminCategoryFormPage({ mode }: { mode: Mode }) {
  const { categoryId = '' } = useParams<{ categoryId: string }>()
  const isEdit = mode === 'edit'
  const idValid = !isEdit || UUID_RE.test(categoryId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const categoriesQuery = useQuery({
    queryKey: adminCategoryQueryKeys.list(),
    queryFn: ({ signal }) => categoriesApi.listAdminCategories(signal),
    enabled: true,
    retry: false,
  })

  const category = useMemo(
    () => categoriesQuery.data?.find((item) => item.id === categoryId),
    [categoriesQuery.data, categoryId],
  )

  const form = useForm<AdminCategoryFormValues>({
    resolver: zodResolver(adminCategoryFormSchema),
    defaultValues: {
      name: '',
      description: '',
      parentId: '',
    },
  })

  useEffect(() => {
    if (isEdit && category) {
      form.reset({
        name: category.name,
        description: category.description ?? '',
        parentId: category.parentId ?? '',
      })
    }
  }, [category, form, isEdit])

  const saveMutation = useMutation({
    mutationFn: async (values: AdminCategoryFormValues) => {
      const payload = {
        name: values.name.trim(),
        description: values.description?.trim() ? values.description.trim() : null,
        parentId: values.parentId ? values.parentId : null,
      }
      if (isEdit) {
        return categoriesApi.updateAdminCategory(categoryId, payload)
      }
      return categoriesApi.createAdminCategory(payload)
    },
    onSuccess: async (saved) => {
      setFormError(null)
      setSuccessMessage(isEdit ? 'Category updated.' : 'Category created.')
      await queryClient.invalidateQueries({ queryKey: adminCategoryQueryKeys.all })
      await queryClient.invalidateQueries({ queryKey: catalogQueryKeys.all })
      void navigate(`/admin/categories/${saved.id}/edit`, { replace: true })
    },
    onError: (error) => {
      if (error instanceof ApiClientError) {
        setFormError(error.userMessage)
        return
      }
      setFormError('Unable to save category.')
    },
  })

  const parentOptions =
    categoriesQuery.data?.filter((item) => item.id !== categoryId && item.active) ?? []

  if (isEdit && !idValid) {
    return (
      <Unavailable title="Invalid category link" message="The category id in the URL is not valid." />
    )
  }

  if (isEdit && categoriesQuery.isLoading) {
    return (
      <div className="mx-auto max-w-xl space-y-4" role="status" aria-live="polite">
        <p className="sr-only">Loading category…</p>
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-56 w-full rounded-xl" />
      </div>
    )
  }

  if (isEdit && categoriesQuery.isSuccess && !category) {
    return (
      <Unavailable
        title="Category not found"
        message="This category does not exist or is not available."
      />
    )
  }

  return (
    <section className={`mx-auto max-w-xl ${adminPageSectionClass}`}>
      <div className="space-y-2">
        <nav aria-label="Breadcrumb" className="text-sm text-muted-foreground">
          <Link to="/admin/categories" className="font-medium text-primary hover:underline">
            Categories
          </Link>
          <span aria-hidden="true"> / </span>
          <span className="text-foreground">{isEdit ? 'Edit' : 'New'}</span>
        </nav>
        <h1 className={adminPageTitleClass}>
          {isEdit ? 'Edit category' : 'Create category'}
        </h1>
        <p className={adminPageLeadClass}>
          Slug is generated by the server from the name. Status is changed from the list page.
        </p>
      </div>

      {isEdit && category ? (
        <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
          <span>
            Slug: <span className="font-mono text-xs text-foreground">{category.slug}</span>
          </span>
          <Badge variant={category.active ? 'success' : 'warning'}>
            {category.active ? 'Active' : 'Inactive'}
          </Badge>
        </div>
      ) : null}

      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>
            <h2 className="text-base font-semibold">Category details</h2>
          </CardTitle>
          <CardDescription>Name, description, and optional parent.</CardDescription>
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
            <div className="space-y-1.5 text-sm">
              <Label htmlFor="category-name">Name</Label>
              <Input
                id="category-name"
                disabled={saveMutation.isPending}
                aria-invalid={Boolean(form.formState.errors.name)}
                {...form.register('name')}
              />
              {form.formState.errors.name ? (
                <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
              ) : null}
            </div>

            <div className="space-y-1.5 text-sm">
              <Label htmlFor="category-description">Description</Label>
              <Textarea
                id="category-description"
                rows={4}
                disabled={saveMutation.isPending}
                aria-invalid={Boolean(form.formState.errors.description)}
                {...form.register('description')}
              />
              {form.formState.errors.description ? (
                <p className="text-xs text-destructive">
                  {form.formState.errors.description.message}
                </p>
              ) : null}
            </div>

            <div className="space-y-1.5 text-sm">
              <Label htmlFor="category-parent">Parent category (optional)</Label>
              <select
                id="category-parent"
                className={adminNativeSelectClass}
                disabled={saveMutation.isPending || categoriesQuery.isLoading}
                aria-invalid={Boolean(form.formState.errors.parentId)}
                {...form.register('parentId')}
              >
                <option value="">None</option>
                {parentOptions.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
              {form.formState.errors.parentId ? (
                <p className="text-xs text-destructive">{form.formState.errors.parentId.message}</p>
              ) : null}
            </div>

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
              <Button type="submit" disabled={saveMutation.isPending}>
                {saveMutation.isPending ? 'Saving…' : isEdit ? 'Save changes' : 'Create category'}
              </Button>
              <Button asChild variant="outline">
                <Link to="/admin/categories">Back to list</Link>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </section>
  )
}

function Unavailable({ title, message }: { title: string; message: string }) {
  return (
    <Card className="border-dashed shadow-none">
      <CardContent className="flex flex-col items-center gap-3 px-6 py-10 text-center">
        <h1 className="text-xl font-semibold text-foreground">{title}</h1>
        <p className="text-sm text-muted-foreground">{message}</p>
        <Button asChild variant="link">
          <Link to="/admin/categories">Back to categories</Link>
        </Button>
      </CardContent>
    </Card>
  )
}
