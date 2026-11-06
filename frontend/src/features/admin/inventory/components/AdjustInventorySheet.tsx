import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { catalogQueryKeys } from '@/features/catalog/hooks/catalog-query-keys'
import { ApiClientError } from '@/lib/api/api-error'

import { adminAlertErrorClass, adminAlertSuccessClass } from '../../lib/admin-ui'
import * as productsApi from '../../products/api/admin-products-api'
import { adminProductQueryKeys } from '../../products/hooks/admin-product-query-keys'
import {
  createAdminInventoryFormSchema,
  type AdminInventoryFormInput,
  type AdminInventoryFormValues,
} from '../../products/schemas'
import { adminInventoryQueryKeys } from '../hooks/admin-inventory-query-keys'
import type { AdminInventoryListItem } from '../types'

type Props = {
  item: AdminInventoryListItem | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function AdjustInventorySheet({ item, open, onOpenChange }: Props) {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const productId = item?.productId ?? ''

  const inventoryQuery = useQuery({
    queryKey: adminProductQueryKeys.inventory(productId),
    queryFn: ({ signal }) => productsApi.getAdminProductInventory(productId, signal),
    enabled: open && Boolean(productId),
    retry: false,
  })

  const resolver = useMemo(() => zodResolver(createAdminInventoryFormSchema(t)), [t])
  const form = useForm<AdminInventoryFormInput, unknown, AdminInventoryFormValues>({
    resolver,
    defaultValues: { quantityOnHand: '0' },
  })

  useEffect(() => {
    if (!open) {
      setFormError(null)
      setSuccessMessage(null)
      return
    }
    if (inventoryQuery.data) {
      form.reset({ quantityOnHand: String(inventoryQuery.data.quantityOnHand) })
    } else if (item) {
      form.reset({ quantityOnHand: String(item.quantityOnHand) })
    }
  }, [open, inventoryQuery.data, item, form])

  const saveMutation = useMutation({
    mutationFn: (values: AdminInventoryFormValues) =>
      productsApi.updateAdminProductInventory(productId, {
        quantityOnHand: values.quantityOnHand,
      }),
    onSuccess: async (updated) => {
      setFormError(null)
      setSuccessMessage('On-hand quantity updated.')
      queryClient.setQueryData(adminProductQueryKeys.inventory(productId), updated)
      form.reset({ quantityOnHand: String(updated.quantityOnHand) })
      await queryClient.invalidateQueries({ queryKey: adminInventoryQueryKeys.all })
      await queryClient.invalidateQueries({ queryKey: adminProductQueryKeys.all })
      await queryClient.invalidateQueries({ queryKey: catalogQueryKeys.all })
    },
    onError: (error) => {
      setSuccessMessage(null)
      setFormError(
        error instanceof ApiClientError ? error.userMessage : t('inventory.adjust.updateError'),
      )
    },
  })

  const inventory = inventoryQuery.data
  const reserved = inventory?.quantityReserved ?? item?.quantityReserved ?? 0
  const onHand = inventory?.quantityOnHand ?? item?.quantityOnHand ?? 0
  const available = inventory?.available ?? item?.available ?? 0
  const busy = saveMutation.isPending || form.formState.isSubmitting

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-md">
        <SheetHeader className="border-b border-border p-4 text-left">
          <SheetTitle>{t('inventory.adjust.title')}</SheetTitle>
          <SheetDescription>
            {item
              ? t('inventory.adjust.descriptionItem', { name: item.name, sku: item.sku })
              : t('inventory.adjust.descriptionNone')}
          </SheetDescription>
        </SheetHeader>

        <div className="flex flex-1 flex-col gap-4 overflow-y-auto p-4">
          {inventoryQuery.isLoading ? (
            <div role="status" aria-live="polite" className="space-y-3">
              <p className="sr-only">{t('inventory.adjust.loading')}</p>
              <Skeleton className="h-20 w-full" />
              <Skeleton className="h-24 w-full" />
            </div>
          ) : inventoryQuery.isError ? (
            <div className={`space-y-3 ${adminAlertErrorClass}`}>
              <p role="alert">
                {inventoryQuery.error instanceof ApiClientError
                  ? inventoryQuery.error.userMessage
                  : t('inventory.adjust.loadError')}
              </p>
              <Button type="button" size="sm" onClick={() => void inventoryQuery.refetch()}>
                {tc('actions.retry')}
              </Button>
            </div>
          ) : (
            <>
              <dl className="grid gap-3 rounded-xl border border-border bg-muted/30 p-3 text-sm sm:grid-cols-3">
                <div>
                  <dt className="text-muted-foreground">{t('inventory.adjust.onHand')}</dt>
                  <dd className="font-medium tabular-nums">{onHand}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">{t('inventory.adjust.reserved')}</dt>
                  <dd className="font-medium tabular-nums" data-testid="adjust-reserved-display">
                    {reserved}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">{t('inventory.adjust.available')}</dt>
                  <dd className="font-medium tabular-nums">{available}</dd>
                </div>
              </dl>
              <p className="text-xs text-muted-foreground">
                {t('inventory.adjust.hint', { reserved })}
              </p>

              <form
                id="adjust-inventory-form"
                className="space-y-4"
                noValidate
                onSubmit={form.handleSubmit((values) => {
                  if (busy) return
                  setSuccessMessage(null)
                  setFormError(null)
                  saveMutation.mutate(values)
                })}
              >
                <div className="space-y-1.5 text-sm">
                  <Label htmlFor="adjust-quantity-on-hand">{t('inventory.adjust.quantity')}</Label>
                  <Input
                    id="adjust-quantity-on-hand"
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
              </form>
            </>
          )}
        </div>

        <SheetFooter className="border-t border-border">
          <Button type="button" variant="outline" disabled={busy} onClick={() => onOpenChange(false)}>
            {tc('actions.close')}
          </Button>
          <Button
            type="submit"
            form="adjust-inventory-form"
            disabled={busy || inventoryQuery.isLoading || inventoryQuery.isError}
          >
            {busy ? t('inventory.adjust.saving') : t('inventory.adjust.submit')}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}
