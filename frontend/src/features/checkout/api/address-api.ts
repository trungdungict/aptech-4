import { apiRequest } from '@/lib/api/api-client'

import type { Address, AddressCreatePayload } from '../types'

export function listAddresses(signal?: AbortSignal): Promise<Address[]> {
  return apiRequest<Address[]>('/addresses', { method: 'GET', auth: true, signal })
}

export function createAddress(
  payload: AddressCreatePayload,
  signal?: AbortSignal,
): Promise<Address> {
  return apiRequest<Address>('/addresses', {
    method: 'POST',
    auth: true,
    body: payload,
    signal,
  })
}
