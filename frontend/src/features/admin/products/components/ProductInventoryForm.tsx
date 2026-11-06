import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { catalogQueryKeys } from '@/features/catalog/hooks/catalog-query-keys'
import { ApiClientError } from '@/lib/api/api-error'

import { adminInventoryQueryKeys } from '../../inventory/hooks/admin-inventory-query-keys'
import { adminAlertErrorClass, adminAlertSuccessClass } from '../../lib/admin-ui'
import * as productsApi from '../api/admin-products-api'
import { adminProductQueryKeys } from '../hooks/admin-product-query-keys'
import {
  createAdminInventoryFormSchema,
  type AdminInventoryFormInput,
  type AdminInventoryFormValues,
} from '../schemas'

type Props = {
  productId: string
}

export function ProductInventoryForm({ productId }: Props) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const inventoryQuery = useQuery({
    queryKey: adminProductQueryKeys.inventory(productId),
    queryFn: ({ signal }) => productsApi.getAdminProductInventory(productId, signal),
    retry: false,
  })

  const resolver = useMemo(() => zodResolver(createAdminInventoryFormSchema(t)), [t])
  const form = useForm<AdminInventoryFormInput, unknown, AdminInventoryFormValues>({
    resolver,
    defaultValues: { quantityOnHand: '0' },
  })

  useEffect(() => {
    if (inventoryQuery.data) {
      form.reset({ quantityOnHand: String(inventoryQuery.data.quantityOnHand) })
    }
  }, [inventoryQuery.data, form])

  const saveMutation = useMutation({
    mutationFn: (values: AdminInventoryFormValues) =>
      productsApi.updateAdminProductInventory(productId, {
        quantityOnHand: values.quantityOnHand,
      }),
    onSuccess: async (updated) => {
      setFormError(null)
      setSuccessMessage(t('products.inventory.updated'))
      queryClient.setQueryData(adminProductQueryKeys.inventory(productId), updated)
      form.reset({ quantityOnHand: String(updated.quantityOnHand) })
      await queryClient.invalidateQueries({ queryKey: adminProductQueryKeys.all })
      await queryClient.invalidateQueries({ queryKey: adminInventoryQueryKeys.all })
      await queryClient.invalidateQueries({ queryKey: catalogQueryKeys.all })
    },
    onError: (error) => {
      if (error instanceof ApiClientError) {
        setFormError(error.userMessage)
        return
      }
      setFormError(t('products.inventory.updateError'))
    },
  })

  if (inventoryQuery.isLoading) {
    return (
      <Card className="shadow-sm" role="status" aria-live="polite">
        <CardHeader>
          <CardTitle>
            <h2 className="text-base font-semibold">{t('products.inventory.title')}</h2>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="sr-only">{t('products.inventory.loading')}</p>
          <Skeleton className="h-24 w-full" />
        </CardContent>
      </Card>
    )
  }

  if (inventoryQuery.isError) {
    return (
      <Card className="shadow-sm">
        <CardHeader>
          <CardTitle>
            <h2 className="text-base font-semibold">{t('products.inventory.title')}</h2>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-destructive" role="alert">
            {inventoryQuery.error instanceof ApiClientError
              ? inventoryQuery.error.userMessage
              : t('products.inventory.loadError')}
          </p>
        </CardContent>
      </Card>
    )
  }

  const inventory = inventoryQuery.data
  const busy = saveMutation.isPending || form.formState.isSubmitting

  return (
    <Card className="shadow-sm">
      <CardHeader>
        <CardTitle>
          <h2 className="text-base font-semibold">{t('products.inventory.title')}</h2>
        </CardTitle>
        <CardDescription>
          {t('products.inventory.description')}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {inventory ? (
          <dl className="mb-4 grid gap-2 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-muted-foreground">{t('products.inventory.onHand')}</dt>
              <dd className="font-medium tabular-nums">{inventory.quantityOnHand}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">{t('products.inventory.reserved')}</dt>
              <dd className="font-medium tabular-nums">{inventory.quantityReserved}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">{t('products.inventory.available')}</dt>
              <dd className="font-medium tabular-nums">{inventory.available}</dd>
            </div>
          </dl>
        ) : null}

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
            <Label htmlFor="product-stock-quantity">{t('products.inventory.quantityLabel')}</Label>
            <Input
              id="product-stock-quantity"
              inputMode="numeric"
              disabled={busy}
              aria-invalid={Boolean(form.formState.errors.quantityOnHand)}
              {...form.register('quantityOnHand')}
            />
            {form.formState.errors.quantityOnHand?.message ? (
              <p className="text-xs text-destructive">
                {form.formState.errors.quantityOnHand.message}
              </p>
            ) : null}
            <p className="text-xs text-muted-foreground">
              {t('products.inventory.hint')}
            </p>
          </div>

          {formError ? (
            <p className={adminAlertErrorClass} role="alert">
              {formError}
            </p>
          ) : null}
          {successMessage ? (
            <p className={adminAlertSuccessClass} role="status">
              {successMessage}
            </p>
          ) : null}

          <Button type="submit" disabled={busy}>
            {busy ? t('products.inventory.updating') : t('products.inventory.submit')}
          </Button>
        </form>
      </CardContent>
    </Card>
  )
}
