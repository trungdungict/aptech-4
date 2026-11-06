import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MegaphoneIcon } from 'lucide-react'
import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { DEFAULT_LIST_PAGE_SIZE, Pagination, useClientListPagination } from '@/components/pagination'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiClientError } from '@/lib/api/api-error'

import {
  adminAlertErrorClass,
  adminPageLeadClass,
  adminPageSectionClass,
  adminPageTitleClass,
  adminTableClass,
  adminTableWrapClass,
  adminTdClass,
  adminThClass,
} from '../../lib/admin-ui'
import * as api from '../api/admin-promotions-api'
import { adminPromotionQueryKeys } from '../hooks/admin-promotion-query-keys'
import { formatUtcInstant } from '../lib/format-utc'
import { useAppLocale } from '@/features/i18n/hooks/useAppLocale'

/** Client-side page size — admin campaigns API returns a full list (no server page contract). */
export const CAMPAIGN_LIST_PAGE_SIZE = DEFAULT_LIST_PAGE_SIZE

const STATUS_KEYS = ['DRAFT', 'SCHEDULED', 'ACTIVE', 'PAUSED', 'EXPIRED'] as const

export function AdminCampaignListPage() {
  const queryClient = useQueryClient()
  const { locale } = useAppLocale()
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const statusLabel = (status: string) =>
    (STATUS_KEYS as readonly string[]).includes(status)
      ? t(`promotions.campaignStatus.${status as (typeof STATUS_KEYS)[number]}`)
      : status
  const [actionError, setActionError] = useState<string | null>(null)

  const campaignsQuery = useQuery({
    queryKey: adminPromotionQueryKeys.campaigns(locale),
    queryFn: ({ signal }) => api.listAdminCampaigns(signal),
    retry: false,
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.deleteAdminCampaign(id),
    onSuccess: async () => {
      setActionError(null)
      await queryClient.invalidateQueries({ queryKey: adminPromotionQueryKeys.all })
    },
    onError: (error) => {
      setActionError(error instanceof ApiClientError ? error.userMessage : t('promotions.campaigns.list.deleteFailed'))
    },
  })

  const campaigns = campaignsQuery.data
  const { page, totalPages, totalElements, pageItems, goToPage } = useClientListPagination(
    campaigns,
    CAMPAIGN_LIST_PAGE_SIZE,
  )

  return (
    <section className={adminPageSectionClass}>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-1">
          <nav aria-label={t('shell.breadcrumb')} className="text-sm text-muted-foreground">
            <Link to="/admin/promotions" className="font-medium text-primary hover:underline">
              {t('promotions.breadcrumb.promotions')}
            </Link>
            <span aria-hidden="true"> / </span>
            <span className="text-foreground">{t('promotions.breadcrumb.campaigns')}</span>
          </nav>
          <h1 className={adminPageTitleClass}>{t('promotions.campaigns.list.title')}</h1>
          <p className={adminPageLeadClass}>
            <Trans
              i18nKey="promotions.campaigns.list.lead"
              ns="admin"
              components={{ strong: <strong /> }}
            />
          </p>
        </div>
        <Button asChild>
          <Link to="/admin/promotions/campaigns/new">{t('promotions.campaigns.list.newCampaign')}</Link>
        </Button>
      </div>

      {actionError ? (
        <p className={adminAlertErrorClass} role="alert">
          {actionError}
        </p>
      ) : null}

      {campaignsQuery.isLoading ? (
        <div className="space-y-3" role="status">
          <Skeleton className="h-12 w-full rounded-xl" />
          <Skeleton className="h-48 w-full rounded-xl" />
        </div>
      ) : campaignsQuery.isError ? (
        <div className={`space-y-3 ${adminAlertErrorClass}`}>
          <p role="alert">
            {campaignsQuery.error instanceof ApiClientError
              ? campaignsQuery.error.userMessage
              : t('promotions.campaigns.list.loadError')}
          </p>
          <Button type="button" size="sm" onClick={() => void campaignsQuery.refetch()}>
            {tc('actions.retry')}
          </Button>
        </div>
      ) : campaigns && campaigns.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <MegaphoneIcon className="size-10 text-muted-foreground opacity-60" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">{t('promotions.campaigns.list.empty')}</p>
            <Button asChild variant="link">
              <Link to="/admin/promotions/campaigns/new">{t('promotions.campaigns.list.createCampaign')}</Link>
            </Button>
          </CardContent>
        </Card>
      ) : campaigns ? (
        <>
          <div className={adminTableWrapClass}>
            <table className={`${adminTableClass} min-w-[56rem]`}>
              <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                <tr>
                  <th className={adminThClass}>{t('promotions.campaigns.list.columns.campaign')}</th>
                  <th className={adminThClass}>{t('promotions.campaigns.list.columns.strategy')}</th>
                  <th className={adminThClass}>{t('promotions.campaigns.list.columns.status')}</th>
                  <th className={adminThClass}>{t('promotions.campaigns.list.columns.window')}</th>
                  <th className={adminThClass}>{t('promotions.campaigns.list.columns.effective')}</th>
                  <th className={adminThClass}>{t('promotions.campaigns.list.columns.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {pageItems.map((campaign) => (
                  <tr key={campaign.id}>
                    <td className={adminTdClass}>
                      <div className="font-medium text-foreground">{campaign.name}</div>
                      <div className="font-mono text-xs text-muted-foreground">{campaign.slug}</div>
                    </td>
                    <td className={adminTdClass}>
                      <div>{campaign.strategy.name}</div>
                      <Badge variant="outline" className="mt-1">
                        {campaign.strategy.type}
                      </Badge>
                    </td>
                    <td className={adminTdClass}>
                      <Badge>{statusLabel(campaign.status)}</Badge>
                      <div className="mt-1 text-xs text-muted-foreground">
                        {t('promotions.campaigns.list.effectiveStatus', {
                          status: statusLabel(campaign.effectiveStatus),
                        })}
                      </div>
                    </td>
                    <td className={`${adminTdClass} font-mono text-xs`}>
                      <div>{formatUtcInstant(campaign.startsAt)}</div>
                      <div>{formatUtcInstant(campaign.endsAt)}</div>
                    </td>
                    <td className={adminTdClass}>
                      <div className="text-xs">
                        {t('promotions.campaigns.list.pricing', {
                          value: campaign.pricingEffective ? t('promotions.flag.yes') : t('promotions.flag.no'),
                        })}
                      </div>
                      <div className="text-xs">
                        {t('promotions.campaigns.list.placement', {
                          value: campaign.placementEffective ? t('promotions.flag.yes') : t('promotions.flag.no'),
                        })}
                      </div>
                      <div className="text-xs text-muted-foreground">
                        {t('promotions.campaigns.list.placements', { n: campaign.placementCount })}
                      </div>
                    </td>
                    <td className={`${adminTdClass} space-x-2 whitespace-nowrap`}>
                      <Button asChild variant="link" className="h-auto p-0">
                        <Link to={`/admin/promotions/campaigns/${campaign.id}`}>{t('promotions.campaigns.list.manage')}</Link>
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        disabled={deleteMutation.isPending}
                        onClick={() => {
                          const ok = window.confirm(
                            t('promotions.campaigns.list.confirmDelete', { name: campaign.name }),
                          )
                          if (!ok) {
                            return
                          }
                          deleteMutation.mutate(campaign.id)
                        }}
                      >
                        {t('promotions.campaigns.list.delete')}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            pageSize={CAMPAIGN_LIST_PAGE_SIZE}
            itemLabel={t('pagination.itemLabels.campaigns')}
            onPageChange={goToPage}
          />
        </>
      ) : null}
    </section>
  )
}
