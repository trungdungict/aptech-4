import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Trans, useTranslation } from 'react-i18next'
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
  NameTranslationsEditor,
  PlacementTranslationsEditor,
} from '@/features/admin/translations'

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
  createAdminCampaignDetailsSchema,
  createAdminPlacementFormSchema,
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
import { useAppLocale } from '@/features/i18n/hooks/useAppLocale'

/** Admin base PUT writes the canonical VI column, so edit forms always read VI text. */
const CANONICAL_LOCALE = 'vi' as const

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

type Tab = 'details' | 'eligibility' | 'placements' | 'preview'
type Mode = 'create' | 'edit'

const TABS: Tab[] = ['details', 'eligibility', 'placements', 'preview']

const CAMPAIGN_STATUS_KEYS = ['DRAFT', 'SCHEDULED', 'ACTIVE', 'PAUSED', 'EXPIRED'] as const

function useCampaignStatusLabel() {
  const { t } = useTranslation('admin')
  return (status: string) =>
    (CAMPAIGN_STATUS_KEYS as readonly string[]).includes(status)
      ? t(`promotions.campaignStatus.${status as (typeof CAMPAIGN_STATUS_KEYS)[number]}`)
      : status
}

export function AdminCampaignEditPage({ mode }: { mode: Mode }) {
  const { campaignId = '' } = useParams<{ campaignId: string }>()
  const isEdit = mode === 'edit'
  const idValid = !isEdit || UUID_RE.test(campaignId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { locale } = useAppLocale()
  const { t } = useTranslation('admin')
  const [searchParams, setSearchParams] = useSearchParams()
  const tabParam = searchParams.get('tab')
  const tab: Tab =
    tabParam === 'eligibility' || tabParam === 'placements' || tabParam === 'preview'
      ? tabParam
      : 'details'

  const [formError, setFormError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)

  const strategiesQuery = useQuery({
    queryKey: adminPromotionQueryKeys.strategies(locale),
    queryFn: ({ signal }) => api.listAdminStrategies(signal),
    retry: false,
  })

  const campaignQuery = useQuery({
    queryKey: adminPromotionQueryKeys.campaign(locale, campaignId),
    queryFn: ({ signal }) => api.getAdminCampaign(campaignId, signal),
    enabled: isEdit && idValid,
    retry: false,
  })

  // Canonical VI copy feeds the base edit form so saving from an EN UI cannot overwrite VI text.
  const canonicalQuery = useQuery({
    queryKey: adminPromotionQueryKeys.campaign(CANONICAL_LOCALE, campaignId),
    queryFn: ({ signal }) => api.getAdminCampaign(campaignId, signal, CANONICAL_LOCALE),
    enabled: isEdit && idValid,
    retry: false,
  })

  const detailsResolver = useMemo(() => zodResolver(createAdminCampaignDetailsSchema(t)), [t])

  const form = useForm<AdminCampaignDetailsInput, unknown, AdminCampaignDetailsValues>({
    resolver: detailsResolver,
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
    if (isEdit && canonicalQuery.data) {
      const c = canonicalQuery.data
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
  }, [canonicalQuery.data, form, isEdit])

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
      setSuccessMessage(
        isEdit ? t('promotions.campaigns.edit.updated') : t('promotions.campaigns.edit.created'),
      )
      await queryClient.invalidateQueries({ queryKey: adminPromotionQueryKeys.all })
      void navigate(`/admin/promotions/campaigns/${saved.id}?tab=details`, { replace: true })
    },
    onError: (error) => {
      setFormError(error instanceof ApiClientError ? error.userMessage : t('promotions.campaigns.edit.saveError'))
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
      setSuccessMessage(t('promotions.campaigns.edit.lifecycleUpdated'))
      await queryClient.invalidateQueries({ queryKey: adminPromotionQueryKeys.all })
    },
    onError: (error) => {
      setFormError(error instanceof ApiClientError ? error.userMessage : t('promotions.campaigns.edit.lifecycleFailed'))
    },
  })

  function setTab(next: Tab) {
    const params = new URLSearchParams(searchParams)
    params.set('tab', next)
    setSearchParams(params, { replace: true })
  }

  if (isEdit && !idValid) {
    return <p className={adminAlertErrorClass}>{t('promotions.campaigns.edit.invalidId')}</p>
  }

  if (isEdit && (campaignQuery.isLoading || canonicalQuery.isLoading)) {
    return (
      <div className="space-y-4" role="status">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-72 w-full rounded-xl" />
      </div>
    )
  }

  if (isEdit && (campaignQuery.isError || canonicalQuery.isError)) {
    const loadError = campaignQuery.error ?? canonicalQuery.error
    return (
      <p className={adminAlertErrorClass} role="alert">
        {loadError instanceof ApiClientError
          ? loadError.userMessage
          : t('promotions.campaigns.edit.notFound')}
      </p>
    )
  }

  const campaign = campaignQuery.data

  return (
    <section className={adminPageSectionClass}>
      <div className="space-y-2">
        <nav aria-label={t('shell.breadcrumb')} className="text-sm text-muted-foreground">
          <Link to="/admin/promotions/campaigns" className="font-medium text-primary hover:underline">
            {t('promotions.breadcrumb.campaigns')}
          </Link>
          <span aria-hidden="true"> / </span>
          <span className="text-foreground">{isEdit ? t('promotions.breadcrumb.manage') : t('promotions.breadcrumb.new')}</span>
        </nav>
        <h1 className={adminPageTitleClass}>
          {isEdit
            ? campaign?.name ?? t('promotions.campaigns.edit.fallbackTitle')
            : t('promotions.campaigns.edit.createTitle')}
        </h1>
        <p className={adminPageLeadClass}>
          <Trans
            i18nKey="promotions.campaigns.edit.lead"
            ns="admin"
            components={{ strong: <strong />, code: <code /> }}
          />
        </p>
      </div>

      {isEdit && campaign ? <CampaignStatusBar campaign={campaign} /> : null}

      {isEdit ? (
        <div className="flex flex-wrap gap-2" role="tablist" aria-label={t('promotions.campaigns.edit.tabsAria')}>
          {TABS.map((item) => (
            <Button
              key={item}
              type="button"
              role="tab"
              aria-selected={tab === item}
              variant={tab === item ? 'default' : 'outline'}
              size="sm"
              onClick={() => setTab(item)}
            >
              {t(`promotions.campaigns.edit.tabs.${item}`)}
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
          campaignId={isEdit ? campaignId : null}
          saving={saveMutation.isPending}
          lifecyclePending={lifecycleMutation.isPending}
          onSave={(values) => saveMutation.mutate(values)}
          onLifecycle={(action) => {
            const labels = {
              activate: t('promotions.campaigns.edit.confirmActivate'),
              pause: t('promotions.campaigns.edit.confirmPause'),
              schedule: t('promotions.campaigns.edit.confirmSchedule'),
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
  const { t } = useTranslation('admin')
  const statusLabel = useCampaignStatusLabel()
  return (
    <div className="flex flex-wrap items-center gap-2 rounded-xl border border-border bg-card px-4 py-3 text-sm">
      <Badge>{statusLabel(campaign.status)}</Badge>
      <span className="text-muted-foreground">{t('promotions.campaigns.edit.effectiveLabel')}</span>
      <Badge variant="outline">{statusLabel(campaign.effectiveStatus)}</Badge>
      <span className="text-muted-foreground">
        {t('promotions.campaigns.edit.pricingPlacement', {
          pricing: campaign.pricingEffective ? t('promotions.flag.on') : t('promotions.flag.off'),
          placement: campaign.placementEffective ? t('promotions.flag.on') : t('promotions.flag.off'),
        })}
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
  campaignId,
  saving,
  lifecyclePending,
  onSave,
  onLifecycle,
}: {
  form: ReturnType<typeof useForm<AdminCampaignDetailsInput, unknown, AdminCampaignDetailsValues>>
  strategies: Array<{ id: string; name: string; type: string; active: boolean }>
  strategiesLoading: boolean
  isEdit: boolean
  campaignId: string | null
  saving: boolean
  lifecyclePending: boolean
  onSave: (values: AdminCampaignDetailsValues) => void
  onLifecycle: (action: 'activate' | 'pause' | 'schedule') => void
}) {
  const { t } = useTranslation('admin')
  return (
    <div className="space-y-6">
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('promotions.campaigns.details.title')}</CardTitle>
      </CardHeader>
      <CardContent>
        <form className="space-y-4" onSubmit={form.handleSubmit(onSave)} noValidate>
          <div className="space-y-1.5">
            <Label htmlFor="strategyId">{t('promotions.campaigns.details.strategy')}</Label>
            <select
              id="strategyId"
              className={adminNativeSelectClass}
              disabled={strategiesLoading}
              {...form.register('strategyId')}
            >
              <option value="">{t('promotions.campaigns.details.selectStrategy')}</option>
              {strategies.map((strategy) => (
                <option key={strategy.id} value={strategy.id}>
                  {t('promotions.campaigns.details.strategyOption', {
                    name: strategy.name,
                    type: strategy.type,
                    suffix: strategy.active ? '' : t('promotions.campaigns.details.inactiveSuffix'),
                  })}
                </option>
              ))}
            </select>
            {form.formState.errors.strategyId ? (
              <p className="text-xs text-destructive">{form.formState.errors.strategyId.message}</p>
            ) : null}
            <p className="text-xs text-muted-foreground">
              {t('promotions.campaigns.details.strategyHint')}
            </p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="name">{t('promotions.campaigns.details.name')}</Label>
              <Input id="name" {...form.register('name')} />
              {form.formState.errors.name ? (
                <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>
              ) : null}
              <p className="text-xs text-muted-foreground" data-testid="campaign-canonical-note">
                {isEdit
                  ? t('promotions.campaigns.details.canonicalNote')
                  : t('promotions.campaigns.details.createHint')}
              </p>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="slug">{t('promotions.campaigns.details.slug')}</Label>
              <Input id="slug" {...form.register('slug')} />
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="startsAt">{t('promotions.campaigns.details.startsAt')}</Label>
              <Input id="startsAt" placeholder="2026-11-01T00:00:00Z" {...form.register('startsAt')} />
              {form.formState.errors.startsAt ? (
                <p className="text-xs text-destructive">{form.formState.errors.startsAt.message}</p>
              ) : null}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="endsAt">{t('promotions.campaigns.details.endsAt')}</Label>
              <Input id="endsAt" placeholder="2026-11-07T23:59:59Z" {...form.register('endsAt')} />
              {form.formState.errors.endsAt ? (
                <p className="text-xs text-destructive">{form.formState.errors.endsAt.message}</p>
              ) : null}
            </div>
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="priority">{t('promotions.campaigns.details.priority')}</Label>
              <Input id="priority" type="number" {...form.register('priority')} />
            </div>
            <label className="flex items-center gap-2 self-end text-sm">
              <input type="checkbox" {...form.register('stackable')} />
              {t('promotions.campaigns.details.stackable')}
            </label>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="notes">{t('promotions.campaigns.details.notes')}</Label>
            <Textarea id="notes" rows={3} {...form.register('notes')} />
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={saving}>
              {saving
                ? t('promotions.campaigns.details.saving')
                : isEdit
                  ? t('promotions.campaigns.details.saveDetails')
                  : t('promotions.campaigns.details.create')}
            </Button>
            {isEdit ? (
              <>
                <Button
                  type="button"
                  variant="secondary"
                  disabled={lifecyclePending}
                  onClick={() => onLifecycle('activate')}
                >
                  {t('promotions.campaigns.details.activate')}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  disabled={lifecyclePending}
                  onClick={() => onLifecycle('pause')}
                >
                  {t('promotions.campaigns.details.pause')}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  disabled={lifecyclePending}
                  onClick={() => onLifecycle('schedule')}
                >
                  {t('promotions.campaigns.details.schedule')}
                </Button>
              </>
            ) : null}
            <Button asChild type="button" variant="ghost">
              <Link to="/admin/promotions/campaigns">{t('promotions.campaigns.details.back')}</Link>
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
    {campaignId ? <NameTranslationsEditor kind="campaign" entityId={campaignId} /> : null}
    </div>
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
  const { locale } = useAppLocale()
  const { t } = useTranslation('admin')
  const [productIds, setProductIds] = useState<string[]>(campaign.productIds)
  const [categoryIds, setCategoryIds] = useState<string[]>(campaign.categoryIds)

  useEffect(() => {
    setProductIds(campaign.productIds)
    setCategoryIds(campaign.categoryIds)
  }, [campaign])

  const categoriesQuery = useQuery({
    queryKey: adminCategoryQueryKeys.list(locale),
    queryFn: ({ signal }) => listAdminCategories(signal),
    retry: false,
  })

  const productsQuery = useQuery({
    queryKey: adminProductQueryKeys.list(locale, { page: 0, size: 100, sort: 'name,asc' }),
    queryFn: ({ signal }) =>
      listAdminProducts({ page: 0, size: 100, sort: 'name,asc', active: true }, signal),
    retry: false,
  })

  const saveMutation = useMutation({
    mutationFn: () =>
      api.replaceAdminCampaignEligibility(campaign.id, { productIds, categoryIds }),
    onSuccess: async () => {
      onError(null)
      await onSuccess(t('promotions.campaigns.eligibility.updated'))
    },
    onError: (error) => {
      onError(error instanceof ApiClientError ? error.userMessage : t('promotions.campaigns.eligibility.failed'))
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
        <CardTitle className="text-base">{t('promotions.campaigns.eligibility.title')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">
          {requiresProducts
            ? t('promotions.campaigns.eligibility.requiresProducts')
            : requiresCategories
              ? t('promotions.campaigns.eligibility.requiresCategories')
              : emptyMeansAll
                ? t('promotions.campaigns.eligibility.emptyMeansAll')
                : t('promotions.campaigns.eligibility.bannerOptional')}
        </p>
        <div className="grid gap-6 lg:grid-cols-2">
          <MultiCheckList
            title={t('promotions.campaigns.eligibility.products')}
            loading={productsQuery.isLoading}
            items={(productsQuery.data?.items ?? []).map((p) => ({
              id: p.id,
              label: `${p.name} (${p.sku})`,
            }))}
            selected={productIds}
            onChange={setProductIds}
          />
          <MultiCheckList
            title={t('promotions.campaigns.eligibility.categories')}
            loading={categoriesQuery.isLoading}
            items={(categoriesQuery.data ?? []).map((c) => ({
              id: c.id,
              label: `${c.name}${c.active ? '' : t('promotions.campaigns.eligibility.inactiveSuffix')}`,
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
          {saveMutation.isPending
            ? t('promotions.campaigns.eligibility.saving')
            : t('promotions.campaigns.eligibility.save')}
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
  const { t } = useTranslation('admin')
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold">{title}</h3>
        <span className="text-xs text-muted-foreground">{t('promotions.campaigns.eligibility.selected', { n: selected.length })}</span>
      </div>
      {loading ? (
        <Skeleton className="h-40 w-full" />
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('promotions.campaigns.eligibility.noOptions')}</p>
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
  const { locale } = useAppLocale()
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const placementsQuery = useQuery({
    queryKey: adminPromotionQueryKeys.placements(locale, campaign.id),
    queryFn: ({ signal }) => api.listAdminCampaignPlacements(campaign.id, signal),
    retry: false,
  })
  // Canonical VI copy for the base form (same cache entry as the list when the UI locale is VI).
  const canonicalPlacementsQuery = useQuery({
    queryKey: adminPromotionQueryKeys.placements(CANONICAL_LOCALE, campaign.id),
    queryFn: ({ signal }) =>
      api.listAdminCampaignPlacements(campaign.id, signal, CANONICAL_LOCALE),
    retry: false,
  })

  const [editing, setEditing] = useState<AdminPromotionPlacement | null>(null)
  const [creating, setCreating] = useState(false)
  const [translatingId, setTranslatingId] = useState<string | null>(null)

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteAdminPlacement(id),
    onSuccess: async () => {
      onError(null)
      await queryClient.invalidateQueries({ queryKey: adminPromotionQueryKeys.placements(locale, campaign.id) })
      await onSuccess(t('promotions.campaigns.placements.deleted'))
    },
    onError: (error) => {
      onError(error instanceof ApiClientError ? error.userMessage : t('promotions.campaigns.placements.deleteFailed'))
    },
  })

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">
          {t('promotions.campaigns.placements.slotsHint')}
        </p>
        <Button
          type="button"
          size="sm"
          onClick={() => {
            setEditing(null)
            setCreating(true)
          }}
        >
          {t('promotions.campaigns.placements.add')}
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
              queryKey: adminPromotionQueryKeys.placements(locale, campaign.id),
            })
            await onSuccess(
              editing
                ? t('promotions.campaigns.placements.updated')
                : t('promotions.campaigns.placements.created'),
            )
          }}
          onError={onError}
        />
      ) : null}

      {translatingId ? (
        <PlacementTranslationsEditor key={translatingId} placementId={translatingId} />
      ) : null}

      {placementsQuery.isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : placementsQuery.isError ? (
        <p className={adminAlertErrorClass} role="alert">
          {t('promotions.campaigns.placements.loadError')}
        </p>
      ) : (
        <div className={adminTableWrapClass}>
          <table className={`${adminTableClass} min-w-[40rem]`}>
            <thead className="border-b border-border bg-muted/40 text-muted-foreground">
              <tr>
                <th className={adminThClass}>{t('promotions.campaigns.placements.columns.key')}</th>
                <th className={adminThClass}>{t('promotions.campaigns.placements.columns.title')}</th>
                <th className={adminThClass}>{t('promotions.campaigns.placements.columns.order')}</th>
                <th className={adminThClass}>{t('promotions.campaigns.placements.columns.enabled')}</th>
                <th className={adminThClass}>{t('promotions.campaigns.placements.columns.actions')}</th>
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
                  <td className={adminTdClass}>{placement.enabled ? t('promotions.flag.yes') : t('promotions.flag.no')}</td>
                  <td className={`${adminTdClass} space-x-2`}>
                    <Button
                      type="button"
                      variant="link"
                      className="h-auto p-0"
                      onClick={() => {
                        setCreating(false)
                        setEditing(
                          canonicalPlacementsQuery.data?.find((p) => p.id === placement.id) ??
                            placement,
                        )
                      }}
                    >
                      {t('promotions.campaigns.placements.edit')}
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      aria-expanded={translatingId === placement.id}
                      onClick={() =>
                        setTranslatingId((current) => (current === placement.id ? null : placement.id))
                      }
                    >
                      {translatingId === placement.id
                        ? t('promotions.campaigns.placements.closeTranslations')
                        : t('promotions.campaigns.placements.translations')}
                    </Button>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => {
                        if (
                          !window.confirm(
                            t('promotions.campaigns.placements.confirmDelete', {
                              title: placement.title,
                            }),
                          )
                        ) {
                          return
                        }
                        deleteMutation.mutate(placement.id)
                      }}
                    >
                      {t('promotions.campaigns.placements.delete')}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {(placementsQuery.data ?? []).length === 0 ? (
            <p className="px-4 py-6 text-sm text-muted-foreground">
              {t('promotions.campaigns.placements.empty')}
            </p>
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
  const { locale } = useAppLocale()
  const { t } = useTranslation('admin')
  const categoriesQuery = useQuery({
    queryKey: adminCategoryQueryKeys.list(locale),
    queryFn: ({ signal }) => listAdminCategories(signal),
    retry: false,
  })
  const productsQuery = useQuery({
    queryKey: adminProductQueryKeys.list(locale, { page: 0, size: 100, sort: 'name,asc' }),
    queryFn: ({ signal }) =>
      listAdminProducts({ page: 0, size: 100, sort: 'name,asc', active: true }, signal),
    retry: false,
  })

  const resolver = useMemo(() => zodResolver(createAdminPlacementFormSchema(t)), [t])

  const form = useForm<AdminPlacementFormInput, unknown, AdminPlacementFormValues>({
    resolver,
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
      onError(error instanceof ApiClientError ? error.userMessage : t('promotions.campaigns.placementForm.saveError'))
    },
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{initial
            ? t('promotions.campaigns.placementForm.editTitle')
            : t('promotions.campaigns.placementForm.newTitle')}</CardTitle>
      </CardHeader>
      <CardContent>
        <form
          className="space-y-4"
          onSubmit={form.handleSubmit((values) => saveMutation.mutate(values))}
          noValidate
        >
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>{t('promotions.campaigns.placementForm.key')}</Label>
              <select className={adminNativeSelectClass} {...form.register('placementKey')}>
                {PLACEMENT_KEYS.map((key) => (
                  <option key={key} value={key}>
                    {key}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label>{t('promotions.campaigns.placementForm.displayOrder')}</Label>
              <Input type="number" {...form.register('displayOrder')} />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="placement-title">{t('promotions.campaigns.placementForm.title')}</Label>
            <Input id="placement-title" {...form.register('title')} />
            {form.formState.errors.title ? (
              <p className="text-xs text-destructive">{form.formState.errors.title.message}</p>
            ) : null}
            <p className="text-xs text-muted-foreground" data-testid="placement-canonical-note">
              {t('promotions.campaigns.placementForm.canonicalNote')}
            </p>
          </div>
          <div className="space-y-1.5">
            <Label>{t('promotions.campaigns.placementForm.subtitle')}</Label>
            <Input {...form.register('subtitle')} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <PromotionImageUploadField
              label={t('promotions.campaigns.placementForm.desktopImage')}
              imageUrl={desktopImageUrl || null}
              onImageUrlChange={(url) => form.setValue('desktopImageUrl', url ?? '', { shouldValidate: true })}
            />
            <PromotionImageUploadField
              label={t('promotions.campaigns.placementForm.mobileImage')}
              imageUrl={mobileImageUrl || null}
              onImageUrlChange={(url) => form.setValue('mobileImageUrl', url ?? '')}
            />
          </div>
          {form.formState.errors.desktopImageUrl ? (
            <p className="text-xs text-destructive">{form.formState.errors.desktopImageUrl.message}</p>
          ) : null}
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>{t('promotions.campaigns.placementForm.ctaType')}</Label>
              <select className={adminNativeSelectClass} {...form.register('ctaType')}>
                {CTA_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label>{t('promotions.campaigns.placementForm.ctaLabel')}</Label>
              <Input {...form.register('ctaLabel')} />
            </div>
          </div>
          {ctaType === 'PRODUCT' ? (
            <div className="space-y-1.5">
              <Label>{t('promotions.campaigns.placementForm.ctaProduct')}</Label>
              <select className={adminNativeSelectClass} {...form.register('ctaProductId')}>
                <option value="">{t('promotions.campaigns.placementForm.select')}</option>
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
              <Label>{t('promotions.campaigns.placementForm.ctaCategory')}</Label>
              <select className={adminNativeSelectClass} {...form.register('ctaCategoryId')}>
                <option value="">{t('promotions.campaigns.placementForm.select')}</option>
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
              <Label>{t('promotions.campaigns.placementForm.collectionKey')}</Label>
              <Input {...form.register('ctaCollectionKey')} />
            </div>
          ) : null}
          {ctaType === 'URL' ? (
            <div className="space-y-1.5">
              <Label>{t('promotions.campaigns.placementForm.ctaUrl')}</Label>
              <Input {...form.register('ctaUrl')} />
              {form.formState.errors.ctaUrl ? (
                <p className="text-xs text-destructive">{form.formState.errors.ctaUrl.message}</p>
              ) : null}
            </div>
          ) : null}
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label>{t('promotions.campaigns.placementForm.optionalStartsAt')}</Label>
              <Input placeholder="2026-11-01T00:00:00Z" {...form.register('startsAt')} />
            </div>
            <div className="space-y-1.5">
              <Label>{t('promotions.campaigns.placementForm.optionalEndsAt')}</Label>
              <Input placeholder="2026-11-07T23:59:59Z" {...form.register('endsAt')} />
            </div>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" {...form.register('enabled')} />
            {t('promotions.campaigns.placementForm.enabled')}
          </label>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={saveMutation.isPending}>
              {saveMutation.isPending
                ? t('promotions.campaigns.placementForm.saving')
                : t('promotions.campaigns.placementForm.save')}
            </Button>
            <Button type="button" variant="outline" onClick={onCancel}>
              {t('promotions.campaigns.placementForm.cancel')}
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  )
}

function PreviewTab({ campaignId }: { campaignId: string }) {
  const { t } = useTranslation('admin')
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
      setError(err instanceof ApiClientError ? err.userMessage : t('promotions.campaigns.preview.failed'))
    },
  })

  const note = useMemo(() => preview?.note, [preview])

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('promotions.campaigns.preview.title')}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-muted-foreground">
          {t('promotions.campaigns.preview.lead')}
        </p>
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1.5">
            <Label htmlFor="previewAt">{t('promotions.campaigns.preview.evaluateAt')}</Label>
            <Input
              id="previewAt"
              placeholder={t('promotions.campaigns.preview.placeholder')}
              value={at}
              onChange={(event) => setAt(event.target.value)}
            />
          </div>
          <Button
            type="button"
            disabled={previewMutation.isPending}
            onClick={() => previewMutation.mutate()}
          >
            {previewMutation.isPending
              ? t('promotions.campaigns.preview.running')
              : t('promotions.campaigns.preview.run')}
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
