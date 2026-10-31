import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'

import { listAdminCategories } from '@/features/admin/categories/api/admin-categories-api'
import { adminCategoryQueryKeys } from '@/features/admin/categories/hooks/admin-category-query-keys'
import { listAdminProducts } from '@/features/admin/products/api/admin-products-api'
import { adminProductQueryKeys } from '@/features/admin/products/hooks/admin-product-query-keys'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { Textarea } from '@/components/ui/textarea'
import { ApiClientError } from '@/lib/api/api-error'

import {
  adminAlertErrorClass,
  adminAlertSuccessClass,
  adminNativeSelectClass,
  adminPageLeadClass,
  adminPageSectionClass,
  adminPageTitleClass,
  adminTableClass,
  adminTableWrapClass,
  adminTdClass,
  adminThClass,
} from '../../lib/admin-ui'
import * as api from '../api/admin-promotions-api'
import { PromotionImageUploadField } from '../components/PromotionImageUploadField'
import { adminPromotionQueryKeys } from '../hooks/admin-promotion-query-keys'
import { formatUtcInstant, toApiUtcInstant } from '../lib/format-utc'
import {
  adminCampaignDetailsSchema,
  adminPlacementFormSchema,
  type AdminCampaignDetailsInput,
  type AdminCampaignDetailsValues,
  type AdminPlacementFormInput,
  type AdminPlacementFormValues,
} from '../schemas'
import type {
  AdminCampaignPreview,
  AdminPromotionCampaign,
  AdminPromotionPlacement,
  PromotionCtaType,
  PromotionPlacementKey,
} from '../types'
import { CTA_TYPES, PLACEMENT_KEYS } from '../types'

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

type Tab = 'details' | 'eligibility' | 'placements' | 'preview'
type Mode = 'create' | 'edit'

const TABS: Array<{ id: Tab; label: string }> = [
  { id: 'details', label: 'Details' },
  { id: 'eligibility', label: 'Eligibility' },
  { id: 'placements', label: 'Placements' },
  { id: 'preview', label: 'Preview' },
]

export function AdminCampaignEditPage({ mode }: { mode: Mode }) {
  const { campaignId = '' } = useParams<{ campaignId: string }>()
  const isEdit = mode === 'edit'
  const idValid = !isEdit || UUID_RE.test(campaignId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const tabParam = searchParams.get('tab')
  const tab: Tab =
    tabParam === 'eligibility' || tabParam === 'placements' || tabParam === 'preview'
      ? tabParam
      : 'details'

  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const strategiesQuery = useQuery({
    queryKey: adminPromotionQueryKeys.strategies(),
    queryFn: ({ signal }) => api.listAdminStrategies(signal),
    retry: false,
  })

  const campaignQuery = useQuery({
    queryKey: adminPromotionQueryKeys.campaign(campaignId),
    queryFn: ({ signal }) => api.getAdminCampaign(campaignId, signal),
    enabled: isEdit && idValid,
    retry: false,
  })

  const form = useForm<AdminCampaignDetailsInput, unknown, AdminCampaignDetailsValues>({
    resolver: zodResolver(adminCampaignDetailsSchema),
    defaultValues: {
      strategyId: '',
      name: '',
      slug: '',
      startsAt: '',
      endsAt: '',
      priority: '100',
      stackable: false,
      notes: '',
    },
  })

  useEffect(() => {
    if (isEdit && campaignQuery.data) {
      const c = campaignQuery.data
      form.reset({
        strategyId: c.strategy.id,
        name: c.name,
        slug: c.slug,
        startsAt: formatUtcInstant(c.startsAt),
        endsAt: formatUtcInstant(c.endsAt),
        priority: String(c.priority),
        stackable: c.stackable,
        notes: c.notes ?? '',
      })
    }
  }, [campaignQuery.data, form, isEdit])

  const saveMutation = useMutation({
    mutationFn: async (values: AdminCampaignDetailsValues) => {
      const payload = {
        strategyId: values.strategyId,
        name: values.name.trim(),
        slug: values.slug?.trim() ? values.slug.trim() : null,
        startsAt: toApiUtcInstant(values.startsAt),
        endsAt: toApiUtcInstant(values.endsAt),
        priority: values.priority,
        stackable: values.stackable,
        notes: values.notes?.trim() ? values.notes.trim() : null,
        productIds: isEdit ? campaignQuery.data?.productIds ?? [] : [],
        categoryIds: isEdit ? campaignQuery.data?.categoryIds ?? [] : [],
      }
      if (isEdit) {
        return api.updateAdminCampaign(campaignId, payload)
      }
      return api.createAdminCampaign(payload)
    },
    onSuccess: async (saved) => {
      setFormError(null)
      setSuccessMessage(isEdit ? 'Campaign updated.' : 'Campaign created as DRAFT.')
      await queryClient.invalidateQueries({ queryKey: adminPromotionQueryKeys.all })
      void navigate(`/admin/promotions/campaigns/${saved.id}?tab=details`, { replace: true })
    },
    onError: (error) => {
      setFormError(error instanceof ApiClientError ? error.userMessage : 'Unable to save campaign.')
    },
  })

  const lifecycleMutation = useMutation({
    mutationFn: async (action: 'activate' | 'pause' | 'schedule') => {
      if (action === 'activate') return api.activateAdminCampaign(campaignId)
      if (action === 'pause') return api.pauseAdminCampaign(campaignId)
      return api.scheduleAdminCampaign(campaignId)
    },
    onSuccess: async () => {
      setFormError(null)
      setSuccessMessage('Campaign lifecycle updated.')
      await queryClient.invalidateQueries({ queryKey: adminPromotionQueryKeys.all })
    },
    onError: (error) => {
      setFormError(error instanceof ApiClientError ? error.userMessage : 'Lifecycle action failed.')
    },
  })

  function setTab(next: Tab) {
    const params = new URLSearchParams(searchParams)
    params.set('tab', next)
    setSearchParams(params, { replace: true })
  }

  if (isEdit && !idValid) {
    return <p className={adminAlertErrorClass}>Invalid campaign id.</p>
  }

  if (isEdit && campaignQuery.isLoading) {
    return (
      <div className="space-y-4" role="status">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-72 w-full rounded-xl" />
      </div>
    )
  }

  if (isEdit && campaignQuery.isError) {
    return (
      <p className={adminAlertErrorClass} role="alert">
        {campaignQuery.error instanceof ApiClientError
          ? campaignQuery.error.userMessage
          : 'Campaign not found.'}
      </p>
    )
  }

  const campaign = campaignQuery.data

  return (
    <section className={adminPageSectionClass}>
      <div className="space-y-2">
        <nav aria-label="Breadcrumb" className="text-sm text-muted-foreground">
          <Link to="/admin/promotions/campaigns" className="font-medium text-primary hover:underline">
            Campaigns
          </Link>
          <span aria-hidden="true"> / </span>
          <span className="text-foreground">{isEdit ? 'Manage' : 'New'}</span>
        </nav>
        <h1 className={adminPageTitleClass}>
          {isEdit ? campaign?.name ?? 'Campaign' : 'Create campaign'}
        </h1>
        <p className={adminPageLeadClass}>
          Enter schedule times as ISO-8601 <strong>UTC</strong> (must end with <code>Z</code> or an
          offset). Values are shown and submitted without silent local conversion.
        </p>
      </div>

      {isEdit && campaign ? <CampaignStatusBar campaign={campaign} /> : null}

      {isEdit ? (
        <div className="flex flex-wrap gap-2" role="tablist" aria-label="Campaign sections">
          {TABS.map((item) => (
            <Button
              key={item.id}
              type="button"
              role="tab"
              aria-selected={tab === item.id}
              variant={tab === item.id ? 'default' : 'outline'}
              size="sm"
              onClick={() => setTab(item.id)}
            >
              {item.label}
            </Button>
          ))}
        </div>
      ) : null}

      {formError ? (
        <p className={adminAlertErrorClass} role="alert">
          {formError}
        </p>
      ) : null}
      {successMessage ? <p className={adminAlertSuccessClass}>{successMessage}</p> : null}

      {(!isEdit || tab === 'details') && (
        <DetailsTab
          form={form}
          strategies={strategiesQuery.data ?? []}
          strategiesLoading={strategiesQuery.isLoading}
          isEdit={isEdit}
          saving={saveMutation.isPending}
          lifecyclePending={lifecycleMutation.isPending}
          onSave={(values) => saveMutation.mutate(values)}
          onLifecycle={(action) => {
            const labels = {
              activate: 'Activate this campaign? It may stay ACTIVE before startsAt (not yet effective).',
              pause: 'Pause this campaign? Placements and pricing become non-effective immediately.',
              schedule: 'Mark this campaign SCHEDULED? startsAt must be in the future.',
            }
            if (!window.confirm(labels[action])) {
              return
            }
            lifecycleMutation.mutate(action)
          }}
        />
      )}

      {isEdit && tab === 'eligibility' && campaign ? (
        <EligibilityTab
          campaign={campaign}
          onError={setFormError}
          onSuccess={async (message) => {
            setSuccessMessage(message)
            await queryClient.invalidateQueries({ queryKey: adminPromotionQueryKeys.all })
          }}
        />
      ) : null}

      {isEdit && tab === 'placements' && campaign ? (
        <PlacementsTab
          campaign={campaign}
          onError={setFormError}
          onSuccess={async (message) => {
            setSuccessMessage(message)
            await queryClient.invalidateQueries({ queryKey: adminPromotionQueryKeys.all })
          }}
        />
      ) : null}

      {isEdit && tab === 'preview' && campaign ? <PreviewTab campaignId={campaign.id} /> : null}
    </section>
  )
}

function CampaignStatusBar({ campaign }: { campaign: AdminPromotionCampaign }) {
  return (
    <div className="flex flex-wrap items-center gap-2 rounded-xl border border-border bg-card px-4 py-3 text-sm">
      <Badge>{campaign.status}</Badge>
      <span className="text-muted-foreground">effective:</span>
      <Badge variant="outline">{campaign.effectiveStatus}</Badge>
      <span className="text-muted-foreground">
        pricing {campaign.pricingEffective ? 'on' : 'off'} · placement{' '}
        {campaign.placementEffective ? 'on' : 'off'}
      </span>
      <span className="font-mono text-xs text-muted-foreground">
        {formatUtcInstant(campaign.startsAt)} → {formatUtcInstant(campaign.endsAt)}
      </span>
    </div>
  )
}

function DetailsTab({
  form,
  strategies,
  strategiesLoading,
  isEdit,
  saving,
  lifecyclePending,
  onSave,
  onLifecycle,
}: {
  form: ReturnType<typeof useForm<AdminCampaignDetailsInput, unknown, AdminCampaignDetailsValues>>
  strategies: Array<{ id: string; name: string; type: string; active: boolean }>
  strategiesLoading: boolean
  isEdit: boolean
  saving: boolean
  lifecyclePending: boolean
  onSave: (values: AdminCampaignDetailsValues) => void
  onLifecycle: (action: 'activate' | 'pause' | 'schedule') => void
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Campaign details</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={form.handleSubmit(onSave)} noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="strategyId">Strategy</Label>
            <select
              id="strategyId"
              className={adminNativeSelectClass}
              disabled={strategiesLoading}
              {...form.register('strategyId')}
            >
              <option value="">Select strategy…</option>
              {strategies.map((strategy) => (
                <option key={strategy.id} value={strategy.id}>
                  {strategy.name} ({strategy.type}){strategy.active ? '' : ' — inactive'}
                </option>
              ))}
            </select>
            {form.formState.errors.strategyId ? (
              <p className="text-xs text-destructive">{form.formState.errors.strategyId.message}</p>
            ) : null}
            <p className="text-xs text-muted-foreground">
              Changing strategy on an ACTIVE campaign is allowed (R4); historical order snapshots stay
              unchanged.
            </p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="name">Name</Label>
              <Input id="name" {...form.register('name')} />
              {form.formState.errors.name ? (
                <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="slug">Slug (optional)</Label>
              <Input id="slug" {...form.register('slug')} />
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="startsAt">startsAt (UTC)</Label>
              <Input id="startsAt" placeholder="2026-11-01T00:00:00Z" {...form.register('startsAt')} />
              {form.formState.errors.startsAt ? (
                <p className="text-xs text-destructive">{form.formState.errors.startsAt.message}</p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="endsAt">endsAt (UTC)</Label>
              <Input id="endsAt" placeholder="2026-11-07T23:59:59Z" {...form.register('endsAt')} />
              {form.formState.errors.endsAt ? (
                <p className="text-xs text-destructive">{form.formState.errors.endsAt.message}</p>
              ) : null}
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="priority">Priority</Label>
              <Input id="priority" type="number" {...form.register('priority')} />
            </div>
            <label className="flex items-center gap-2 self-end text-sm">
              <input type="checkbox" {...form.register('stackable')} />
              Stackable
            </label>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="notes">Notes</Label>
            <Textarea id="notes" rows={3} {...form.register('notes')} />
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={saving}>
              {saving ? 'Saving…' : isEdit ? 'Save details' : 'Create campaign'}
            </Button>
            {isEdit ? (
              <>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={lifecyclePending}
                  onClick={() => onLifecycle('activate')}
                >
                  Activate
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  disabled={lifecyclePending}
                  onClick={() => onLifecycle('pause')}
                >
                  Pause
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  disabled={lifecyclePending}
                  onClick={() => onLifecycle('schedule')}
                >
                  Schedule
                </Button>
              </>
            ) : null}
            <Button asChild type="button" variant="ghost">
              <Link to="/admin/promotions/campaigns">Back</Link>
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

function EligibilityTab({
  campaign,
  onError,
  onSuccess,
}: {
  campaign: AdminPromotionCampaign
  onError: (message: string | null) => void
  onSuccess: (message: string) => Promise<void>
}) {
  const [productIds, setProductIds] = useState<string[]>(campaign.productIds)
  const [categoryIds, setCategoryIds] = useState<string[]>(campaign.categoryIds)

  useEffect(() => {
    setProductIds(campaign.productIds)
    setCategoryIds(campaign.categoryIds)
  }, [campaign])

  const categoriesQuery = useQuery({
    queryKey: adminCategoryQueryKeys.list(),
    queryFn: ({ signal }) => listAdminCategories(signal),
    retry: false,
  })

  const productsQuery = useQuery({
    queryKey: adminProductQueryKeys.list({ page: 0, size: 100, sort: 'name,asc' }),
    queryFn: ({ signal }) =>
      listAdminProducts({ page: 0, size: 100, sort: 'name,asc', active: true }, signal),
    retry: false,
  })

  const saveMutation = useMutation({
    mutationFn: () =>
      api.replaceAdminCampaignEligibility(campaign.id, { productIds, categoryIds }),
    onSuccess: async () => {
      onError(null)
      await onSuccess('Eligibility updated.')
    },
    onError: (error) => {
      onError(error instanceof ApiClientError ? error.userMessage : 'Eligibility update failed.')
    },
  })

  const strategyType = campaign.strategy.type
  const emptyMeansAll =
    strategyType === 'PERCENTAGE_DISCOUNT' || strategyType === 'FIXED_AMOUNT_DISCOUNT'
  const requiresProducts = strategyType === 'PRODUCT_DISCOUNT'
  const requiresCategories = strategyType === 'CATEGORY_DISCOUNT'

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Eligibility</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">
          {requiresProducts
            ? 'PRODUCT_DISCOUNT requires at least one product.'
            : requiresCategories
              ? 'CATEGORY_DISCOUNT requires at least one category.'
              : emptyMeansAll
                ? 'Empty product and category sets mean all cart lines (for this strategy type).'
                : 'BANNER_ONLY eligibility is optional (placements only).'}
        </p>
        <div className="grid gap-6 lg:grid-cols-2">
          <MultiCheckList
            title="Products"
            loading={productsQuery.isLoading}
            items={(productsQuery.data?.items ?? []).map((p) => ({
              id: p.id,
              label: `${p.name} (${p.sku})`,
            }))}
            selected={productIds}
            onChange={setProductIds}
          />
          <MultiCheckList
            title="Categories"
            loading={categoriesQuery.isLoading}
            items={(categoriesQuery.data ?? []).map((c) => ({
              id: c.id,
              label: `${c.name}${c.active ? '' : ' (inactive)'}`,
            }))}
            selected={categoryIds}
            onChange={setCategoryIds}
          />
        </div>
        <Button
          type="button"
          disabled={saveMutation.isPending}
          onClick={() => saveMutation.mutate()}
        >
          {saveMutation.isPending ? 'Saving…' : 'Save eligibility'}
        </Button>
      </CardContent>
    </Card>
  )
}

function MultiCheckList({
  title,
  loading,
  items,
  selected,
  onChange,
}: {
  title: string
  loading: boolean
  items: Array<{ id: string; label: string }>
  selected: string[]
  onChange: (ids: string[]) => void
}) {
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold">{title}</h3>
        <span className="text-xs text-muted-foreground">{selected.length} selected</span>
      </div>
      {loading ? (
        <Skeleton className="h-40 w-full" />
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">No options available.</p>
      ) : (
        <ul className="max-h-64 space-y-1 overflow-y-auto rounded-lg border border-border p-3">
          {items.map((item) => {
            const checked = selected.includes(item.id)
            return (
              <li key={item.id}>
                <label className="flex items-start gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => {
                      onChange(
                        checked
                          ? selected.filter((id) => id !== item.id)
                          : [...selected, item.id],
                      )
                    }}
                  />
                  <span>{item.label}</span>
                </label>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

function PlacementsTab({
  campaign,
  onError,
  onSuccess,
}: {
  campaign: AdminPromotionCampaign
  onError: (message: string | null) => void
  onSuccess: (message: string) => Promise<void>
}) {
  const queryClient = useQueryClient()
  const placementsQuery = useQuery({
    queryKey: adminPromotionQueryKeys.placements(campaign.id),
    queryFn: ({ signal }) => api.listAdminCampaignPlacements(campaign.id, signal),
    retry: false,
  })

  const [editing, setEditing] = useState<AdminPromotionPlacement | null>(null)
  const [creating, setCreating] = useState(false)

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteAdminPlacement(id),
    onSuccess: async () => {
      onError(null)
      await queryClient.invalidateQueries({ queryKey: adminPromotionQueryKeys.placements(campaign.id) })
      await onSuccess('Placement deleted.')
    },
    onError: (error) => {
      onError(error instanceof ApiClientError ? error.userMessage : 'Delete failed.')
    },
  })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">
          Homepage slots: HOME_HERO (carousel), HOME_PROMO_TOP, HOME_PROMO_BOTTOM.
        </p>
        <Button
          type="button"
          size="sm"
          onClick={() => {
            setEditing(null)
            setCreating(true)
          }}
        >
          Add placement
        </Button>
      </div>

      {creating || editing ? (
        <PlacementForm
          campaignId={campaign.id}
          initial={editing}
          onCancel={() => {
            setCreating(false)
            setEditing(null)
          }}
          onSaved={async () => {
            setCreating(false)
            setEditing(null)
            await queryClient.invalidateQueries({
              queryKey: adminPromotionQueryKeys.placements(campaign.id),
            })
            await onSuccess(editing ? 'Placement updated.' : 'Placement created.')
          }}
          onError={onError}
        />
      ) : null}

      {placementsQuery.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : placementsQuery.isError ? (
        <p className={adminAlertErrorClass} role="alert">
          Unable to load placements.
        </p>
      ) : (
        <div className={adminTableWrapClass}>
          <table className={`${adminTableClass} min-w-[40rem]`}>
            <thead className="border-b border-border bg-muted/40 text-muted-foreground">
              <tr>
                <th className={adminThClass}>Key</th>
                <th className={adminThClass}>Title</th>
                <th className={adminThClass}>Order</th>
                <th className={adminThClass}>Enabled</th>
                <th className={adminThClass}>Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {(placementsQuery.data ?? []).map((placement) => (
                <tr key={placement.id}>
                  <td className={adminTdClass}>
                    <Badge variant="outline">{placement.placementKey}</Badge>
                  </td>
                  <td className={adminTdClass}>{placement.title}</td>
                  <td className={adminTdClass}>{placement.displayOrder}</td>
                  <td className={adminTdClass}>{placement.enabled ? 'yes' : 'no'}</td>
                  <td className={`${adminTdClass} space-x-2`}>
                    <Button
                      type="button"
                      variant="link"
                      className="h-auto p-0"
                      onClick={() => {
                        setCreating(false)
                        setEditing(placement)
                      }}
                    >
                      Edit
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => {
                        if (!window.confirm(`Delete placement “${placement.title}”?`)) {
                          return
                        }
                        deleteMutation.mutate(placement.id)
                      }}
                    >
                      Delete
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {(placementsQuery.data ?? []).length === 0 ? (
            <p className="px-4 py-6 text-sm text-muted-foreground">No placements yet.</p>
          ) : null}
        </div>
      )}
    </div>
  )
}

function PlacementForm({
  campaignId,
  initial,
  onCancel,
  onSaved,
  onError,
}: {
  campaignId: string
  initial: AdminPromotionPlacement | null
  onCancel: () => void
  onSaved: () => Promise<void>
  onError: (message: string | null) => void
}) {
  const categoriesQuery = useQuery({
    queryKey: adminCategoryQueryKeys.list(),
    queryFn: ({ signal }) => listAdminCategories(signal),
    retry: false,
  })
  const productsQuery = useQuery({
    queryKey: adminProductQueryKeys.list({ page: 0, size: 100, sort: 'name,asc' }),
    queryFn: ({ signal }) =>
      listAdminProducts({ page: 0, size: 100, sort: 'name,asc', active: true }, signal),
    retry: false,
  })

  const form = useForm<AdminPlacementFormInput, unknown, AdminPlacementFormValues>({
    resolver: zodResolver(adminPlacementFormSchema),
    defaultValues: {
      placementKey: initial?.placementKey ?? 'HOME_HERO',
      title: initial?.title ?? '',
      subtitle: initial?.subtitle ?? '',
      ctaLabel: initial?.ctaLabel ?? '',
      ctaType: initial?.ctaType ?? 'NONE',
      ctaProductId: initial?.ctaProductId ?? '',
      ctaCategoryId: initial?.ctaCategoryId ?? '',
      ctaCollectionKey: initial?.ctaCollectionKey ?? '',
      ctaUrl: initial?.ctaUrl ?? '',
      desktopImageUrl: initial?.desktopImageUrl ?? '',
      mobileImageUrl: initial?.mobileImageUrl ?? '',
      displayOrder: String(initial?.displayOrder ?? 0),
      startsAt: initial?.startsAt ? formatUtcInstant(initial.startsAt) : '',
      endsAt: initial?.endsAt ? formatUtcInstant(initial.endsAt) : '',
      enabled: initial?.enabled ?? true,
    },
  })

  const ctaType = form.watch('ctaType')
  const desktopImageUrl = form.watch('desktopImageUrl')
  const mobileImageUrl = form.watch('mobileImageUrl')

  const saveMutation = useMutation({
    mutationFn: async (values: AdminPlacementFormValues) => {
      const payload = {
        placementKey: values.placementKey as PromotionPlacementKey,
        title: values.title.trim(),
        subtitle: values.subtitle?.trim() || null,
        ctaLabel: values.ctaLabel?.trim() || null,
        ctaType: values.ctaType as PromotionCtaType,
        ctaProductId: values.ctaProductId?.trim() || null,
        ctaCategoryId: values.ctaCategoryId?.trim() || null,
        ctaCollectionKey: values.ctaCollectionKey?.trim() || null,
        ctaUrl: values.ctaUrl?.trim() || null,
        desktopImageUrl: values.desktopImageUrl.trim(),
        mobileImageUrl: values.mobileImageUrl?.trim() || null,
        displayOrder: values.displayOrder,
        startsAt: values.startsAt?.trim() ? toApiUtcInstant(values.startsAt) : null,
        endsAt: values.endsAt?.trim() ? toApiUtcInstant(values.endsAt) : null,
        enabled: values.enabled,
      }
      if (initial) {
        return api.updateAdminPlacement(initial.id, payload)
      }
      return api.createAdminPlacement(campaignId, payload)
    },
    onSuccess: async () => {
      onError(null)
      await onSaved()
    },
    onError: (error) => {
      onError(error instanceof ApiClientError ? error.userMessage : 'Unable to save placement.')
    },
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{initial ? 'Edit placement' : 'New placement'}</CardTitle>
      </CardHeader>
      <CardContent>
        <form
          className="space-y-4"
          onSubmit={form.handleSubmit((values) => saveMutation.mutate(values))}
          noValidate
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>Placement key</Label>
              <select className={adminNativeSelectClass} {...form.register('placementKey')}>
                {PLACEMENT_KEYS.map((key) => (
                  <option key={key} value={key}>
                    {key}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label>Display order</Label>
              <Input type="number" {...form.register('displayOrder')} />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="placement-title">Title</Label>
            <Input id="placement-title" {...form.register('title')} />
            {form.formState.errors.title ? (
              <p className="text-xs text-destructive">{form.formState.errors.title.message}</p>
            ) : null}
          </div>
          <div className="space-y-1.5">
            <Label>Subtitle</Label>
            <Input {...form.register('subtitle')} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <PromotionImageUploadField
              label="Desktop image"
              imageUrl={desktopImageUrl || null}
              onImageUrlChange={(url) => form.setValue('desktopImageUrl', url ?? '', { shouldValidate: true })}
            />
            <PromotionImageUploadField
              label="Mobile image (optional)"
              imageUrl={mobileImageUrl || null}
              onImageUrlChange={(url) => form.setValue('mobileImageUrl', url ?? '')}
            />
          </div>
          {form.formState.errors.desktopImageUrl ? (
            <p className="text-xs text-destructive">{form.formState.errors.desktopImageUrl.message}</p>
          ) : null}
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>CTA type</Label>
              <select className={adminNativeSelectClass} {...form.register('ctaType')}>
                {CTA_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label>CTA label</Label>
              <Input {...form.register('ctaLabel')} />
            </div>
          </div>
          {ctaType === 'PRODUCT' ? (
            <div className="space-y-1.5">
              <Label>CTA product</Label>
              <select className={adminNativeSelectClass} {...form.register('ctaProductId')}>
                <option value="">Select…</option>
                {(productsQuery.data?.items ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>
          ) : null}
          {ctaType === 'CATEGORY' ? (
            <div className="space-y-1.5">
              <Label>CTA category</Label>
              <select className={adminNativeSelectClass} {...form.register('ctaCategoryId')}>
                <option value="">Select…</option>
                {(categoriesQuery.data ?? []).map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
          ) : null}
          {ctaType === 'COLLECTION' ? (
            <div className="space-y-1.5">
              <Label>Collection key</Label>
              <Input {...form.register('ctaCollectionKey')} />
            </div>
          ) : null}
          {ctaType === 'URL' ? (
            <div className="space-y-1.5">
              <Label>CTA URL (http(s) or /path)</Label>
              <Input {...form.register('ctaUrl')} />
              {form.formState.errors.ctaUrl ? (
                <p className="text-xs text-destructive">{form.formState.errors.ctaUrl.message}</p>
              ) : null}
            </div>
          ) : null}
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>Optional startsAt (UTC)</Label>
              <Input placeholder="2026-11-01T00:00:00Z" {...form.register('startsAt')} />
            </div>
            <div className="space-y-1.5">
              <Label>Optional endsAt (UTC)</Label>
              <Input placeholder="2026-11-07T23:59:59Z" {...form.register('endsAt')} />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" {...form.register('enabled')} />
            Enabled
          </label>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={saveMutation.isPending}>
              {saveMutation.isPending ? 'Saving…' : 'Save placement'}
            </Button>
            <Button type="button" variant="outline" onClick={onCancel}>
              Cancel
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

function PreviewTab({ campaignId }: { campaignId: string }) {
  const [at, setAt] = useState('')
  const [preview, setPreview] = useState<AdminCampaignPreview | null>(null)
  const [error, setError] = useState<string | null>(null)

  const previewMutation = useMutation({
    mutationFn: () => api.previewAdminCampaign(campaignId, at.trim() || null),
    onSuccess: (data) => {
      setError(null)
      setPreview(data)
    },
    onError: (err) => {
      setPreview(null)
      setError(err instanceof ApiClientError ? err.userMessage : 'Preview failed.')
    },
  })

  const note = useMemo(() => preview?.note, [preview])

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">Schedule preview</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">
          Reports status / effectiveStatus / pricingEffective / placementEffective at a UTC instant.
          Discount amount calculation is deferred to checkout (P22).
        </p>
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="previewAt">Evaluate at (UTC, optional)</Label>
            <Input
              id="previewAt"
              placeholder="Leave blank for server now"
              value={at}
              onChange={(event) => setAt(event.target.value)}
            />
          </div>
          <Button
            type="button"
            disabled={previewMutation.isPending}
            onClick={() => previewMutation.mutate()}
          >
            {previewMutation.isPending ? 'Running…' : 'Run preview'}
          </Button>
        </div>
        {error ? (
          <p className={adminAlertErrorClass} role="alert">
            {error}
          </p>
        ) : null}
        {preview ? (
          <dl className="grid gap-2 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-muted-foreground">evaluatedAt</dt>
              <dd className="font-mono text-xs">{formatUtcInstant(preview.evaluatedAt)}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">status</dt>
              <dd>{preview.status}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">effectiveStatus</dt>
              <dd>{preview.effectiveStatus}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">pricingEffective</dt>
              <dd>{preview.pricingEffective ? 'true' : 'false'}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">placementEffective</dt>
              <dd>{preview.placementEffective ? 'true' : 'false'}</dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-muted-foreground">note</dt>
              <dd>{note}</dd>
            </div>
          </dl>
        ) : null}
      </CardContent>
    </Card>
  )
}
