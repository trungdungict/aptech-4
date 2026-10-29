import { apiRequest } from '@/lib/api/api-client'

import type { CheckoutPayload, CheckoutResult } from '../types'

export function checkout(payload: CheckoutPayload, signal?: AbortSignal): Promise<CheckoutResult> {
  return apiRequest<CheckoutResult>('/checkout', {
    method: 'POST',
    auth: true,
    body: payload,
    signal,
  })
}
