import { Link } from 'react-router-dom'
import { MegaphoneIcon, TagsIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

import { adminPageLeadClass, adminPageSectionClass, adminPageTitleClass } from '../../lib/admin-ui'

export function AdminPromotionsHubPage() {
  return (
    <section className={`mx-auto max-w-4xl ${adminPageSectionClass}`}>
      <div className="space-y-2">
        <h1 className={adminPageTitleClass}>Promotions</h1>
        <p className={`max-w-2xl ${adminPageLeadClass}`}>
          Manage strategies (rule templates), campaigns (scheduled instances), eligibility, and
          homepage placements. Checkout discount application is out of scope for this Admin module
          (P22).
        </p>
      </div>

      <ul className="grid gap-4 sm:grid-cols-2">
        <li>
          <Card className="h-full shadow-sm">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <TagsIcon className="size-4 text-primary" aria-hidden="true" />
                Strategies
              </CardTitle>
              <CardDescription>
                Create reusable discount/banner templates and activate or deactivate them.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Button asChild>
                <Link to="/admin/promotions/strategies">Open strategies</Link>
              </Button>
            </CardContent>
          </Card>
        </li>
        <li>
          <Card className="h-full shadow-sm">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <MegaphoneIcon className="size-4 text-primary" aria-hidden="true" />
                Campaigns
              </CardTitle>
              <CardDescription>
                Schedule campaigns, manage eligibility and placements, activate / pause / preview.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Button asChild>
                <Link to="/admin/promotions/campaigns">Open campaigns</Link>
              </Button>
            </CardContent>
          </Card>
        </li>
      </ul>
    </section>
  )
}
