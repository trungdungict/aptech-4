import { apiRequest } from '@/lib/api/api-client'

import type { AddCartItemPayload, Cart, UpdateCartItemPayload } from '../types'

export function getCart(signal?: AbortSignal): Promise<Cart> {
  return apiRequest<Cart>('/cart', { method: 'GET', auth: true, signal })
}

export function addCartItem(payload: AddCartItemPayload, signal?: AbortSignal): Promise<Cart> {
  return apiRequest<Cart>('/cart/items', {
    method: 'POST',
    auth: true,
    body: payload,
    signal,
  })
}

export function updateCartItem(
  itemId: string,
  payload: UpdateCartItemPayload,
  signal?: AbortSignal,
): Promise<Cart> {
  return apiRequest<Cart>(`/cart/items/${itemId}`, {
    method: 'PATCH',
    auth: true,
    body: payload,
    signal,
  })
}

export function removeCartItem(itemId: string, signal?: AbortSignal): Promise<Cart> {
  return apiRequest<Cart>(`/cart/items/${itemId}`, {
    method: 'DELETE',
    auth: true,
    signal,
  })
}

export function clearCart(signal?: AbortSignal): Promise<Cart> {
  return apiRequest<Cart>('/cart/items', {
    method: 'DELETE',
    auth: true,
    signal,
  })
}
