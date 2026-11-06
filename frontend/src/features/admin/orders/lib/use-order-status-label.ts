import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import { isKnownOrderStatus } from './admin-order-transitions'

/**
 * Localized label for a backend order status code (display only — the raw code is still
 * what is sent to / received from the API). Unknown codes fall back to the raw value.
 */
export function useOrderStatusLabel(): (status: string) => string {
  const { t } = useTranslation('orders')
  return useCallback(
    (status: string) => (isKnownOrderStatus(status) ? t(`statusCode.${status}`) : status),
    [t],
  )
}
