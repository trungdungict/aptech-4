import { useTranslation } from 'react-i18next'

import { Badge } from '@/components/ui/badge'

type OrderStatusBadgeProps = {
  status: string
}

const KNOWN_STATUSES = [
  'PAID',
  'COMPLETED',
  'PENDING_PAYMENT',
  'PROCESSING',
  'SHIPPED',
  'FAILED',
  'CANCELLED',
] as const

type KnownStatus = (typeof KNOWN_STATUSES)[number]

function isKnownStatus(status: string): status is KnownStatus {
  return (KNOWN_STATUSES as readonly string[]).includes(status)
}

/**
 * Visual mapping for backend OrderStatus — labels localized; raw code kept for unknown values.
 */
export function OrderStatusBadge({ status }: OrderStatusBadgeProps) {
  const { t } = useTranslation('orders')
  const label = isKnownStatus(status) ? t(`statusCode.${status}`) : status

  switch (status) {
    case 'PAID':
    case 'COMPLETED':
      return <Badge variant="success">{label}</Badge>
    case 'PENDING_PAYMENT':
    case 'PROCESSING':
    case 'SHIPPED':
      return <Badge variant="secondary">{label}</Badge>
    case 'FAILED':
    case 'CANCELLED':
      return <Badge variant="destructive">{label}</Badge>
    default:
      return <Badge variant="outline">{label}</Badge>
  }
}
