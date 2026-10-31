import { Badge } from '@/components/ui/badge'

type OrderStatusBadgeProps = {
  status: string
}

/**
 * Visual mapping for backend OrderStatus strings only — no invented labels or transitions.
 */
export function OrderStatusBadge({ status }: OrderStatusBadgeProps) {
  switch (status) {
    case 'PAID':
    case 'COMPLETED':
      return <Badge variant="success">{status}</Badge>
    case 'PENDING_PAYMENT':
    case 'PROCESSING':
    case 'SHIPPED':
      return <Badge variant="secondary">{status}</Badge>
    case 'FAILED':
    case 'CANCELLED':
      return <Badge variant="destructive">{status}</Badge>
    default:
      return <Badge variant="outline">{status}</Badge>
  }
}
