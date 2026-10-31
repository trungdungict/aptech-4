import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MegaphoneIcon } from 'lucide-react'
import { useState } from 'react'
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

/** Client-side page size — admin campaigns API returns a full list (no server page contract). */
export const CAMPAIGN_LIST_PAGE_SIZE = DEFAULT_LIST_PAGE_SIZE

export function AdminCampaignListPage() {
  const queryClient = useQueryClient()
  const [actionError, setActionError] = useState<string | null>(null)

  const campaignsQuery = useQuery({
    queryKey: adminPromotionQueryKeys.campaigns(),
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
      setActionError(error instanceof ApiClientError ? error.userMessage : 'Delete failed.')
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
          <nav aria-label="Breadcrumb" className="text-sm text-muted-foreground">
            <Link to="/admin/promotions" className="font-medium text-primary hover:underline">
              Promotions
            </Link>
            <span aria-hidden="true"> / </span>
            <span className="text-foreground">Campaigns</span>
          </nav>
          <h1 className={adminPageTitleClass}>Promotion campaigns</h1>
          <p className={adminPageLeadClass}>
            Schedules are stored and evaluated in <strong>UTC</strong>. Status is manual; effective
            flags combine status ∧ inclusive window ∧ strategy active.
          </p>
        </div>
        <Button asChild>
          <Link to="/admin/promotions/campaigns/new">New campaign</Link>
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
              : 'Unable to load campaigns.'}
          </p>
          <Button type="button" size="sm" onClick={() => void campaignsQuery.refetch()}>
            Retry
          </Button>
        </div>
      ) : campaigns && campaigns.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <MegaphoneIcon className="size-10 text-muted-foreground opacity-60" aria-hidden="true" />
            <p className="text-sm text-muted-foreground">No campaigns yet.</p>
            <Button asChild variant="link">
              <Link to="/admin/promotions/campaigns/new">Create a campaign</Link>
            </Button>
          </CardContent>
        </Card>
      ) : campaigns ? (
        <>
          <div className={adminTableWrapClass}>
            <table className={`${adminTableClass} min-w-[56rem]`}>
              <thead className="border-b border-border bg-muted/40 text-muted-foreground">
                <tr>
                  <th className={adminThClass}>Campaign</th>
                  <th className={adminThClass}>Strategy</th>
                  <th className={adminThClass}>Status</th>
                  <th className={adminThClass}>UTC window</th>
                  <th className={adminThClass}>Effective</th>
                  <th className={adminThClass}>Actions</th>
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
                      <Badge>{campaign.status}</Badge>
                      <div className="mt-1 text-xs text-muted-foreground">
                        effective: {campaign.effectiveStatus}
                      </div>
                    </td>
                    <td className={`${adminTdClass} font-mono text-xs`}>
                      <div>{formatUtcInstant(campaign.startsAt)}</div>
                      <div>{formatUtcInstant(campaign.endsAt)}</div>
                    </td>
                    <td className={adminTdClass}>
                      <div className="text-xs">
                        pricing: {campaign.pricingEffective ? 'yes' : 'no'}
                      </div>
                      <div className="text-xs">
                        placement: {campaign.placementEffective ? 'yes' : 'no'}
                      </div>
                      <div className="text-xs text-muted-foreground">
                        placements: {campaign.placementCount}
                      </div>
                    </td>
                    <td className={`${adminTdClass} space-x-2 whitespace-nowrap`}>
                      <Button asChild variant="link" className="h-auto p-0">
                        <Link to={`/admin/promotions/campaigns/${campaign.id}`}>Manage</Link>
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        disabled={deleteMutation.isPending}
                        onClick={() => {
                          const ok = window.confirm(
                            `Delete campaign “${campaign.name}”? Placements and eligibility rows will be removed. Historical order snapshots are unaffected.`,
                          )
                          if (!ok) {
                            return
                          }
                          deleteMutation.mutate(campaign.id)
                        }}
                      >
                        Delete
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
            itemLabel="campaigns"
            onPageChange={goToPage}
          />
        </>
      ) : null}
    </section>
  )
}
