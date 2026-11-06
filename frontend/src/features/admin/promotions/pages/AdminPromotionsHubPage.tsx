import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { MegaphoneIcon, TagsIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

import { adminPageLeadClass, adminPageSectionClass, adminPageTitleClass } from '../../lib/admin-ui'

export function AdminPromotionsHubPage() {
  const { t } = useTranslation('admin')
  return (
    <section className={`mx-auto max-w-4xl ${adminPageSectionClass}`}>
      <div className="space-y-2">
        <h1 className={adminPageTitleClass}>{t('promotions.hub.title')}</h1>
        <p className={`max-w-2xl ${adminPageLeadClass}`}>
          {t('promotions.hub.lead')}
        </p>
      </div>

      <ul className="grid gap-4 sm:grid-cols-2">
        <li>
          <Card className="h-full shadow-sm">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <TagsIcon className="size-4 text-primary" aria-hidden="true" />
                {t('promotions.hub.strategiesTitle')}
              </CardTitle>
              <CardDescription>
                {t('promotions.hub.strategiesDescription')}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Button asChild>
                <Link to="/admin/promotions/strategies">{t('promotions.hub.openStrategies')}</Link>
              </Button>
            </CardContent>
          </Card>
        </li>
        <li>
          <Card className="h-full shadow-sm">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <MegaphoneIcon className="size-4 text-primary" aria-hidden="true" />
                {t('promotions.hub.campaignsTitle')}
              </CardTitle>
              <CardDescription>
                {t('promotions.hub.campaignsDescription')}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Button asChild>
                <Link to="/admin/promotions/campaigns">{t('promotions.hub.openCampaigns')}</Link>
              </Button>
            </CardContent>
          </Card>
        </li>
      </ul>
    </section>
  )
}
