import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'

import { useAuth } from '@/features/auth/hooks/useAuth'
import * as cartApi from '@/features/cart/api/cart-api'
import { cartQueryKeys } from '@/features/cart/hooks/cart-query-keys'
import { formatProductPrice } from '@/features/catalog/lib/format-price'
import { orderQueryKeys } from '@/features/orders/hooks/order-query-keys'
import { ApiClientError } from '@/lib/api/api-error'

import * as addressApi from '../api/address-api'
import * as checkoutApi from '../api/checkout-api'
import { addressQueryKeys } from '../hooks/address-query-keys'
import { addressCreateSchema, type AddressCreateFormValues } from '../schemas'
import type { CheckoutResult } from '../types'

function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `checkout-${Date.now()}-${Math.random().toString(36).slice(2, 12)}`
}

export function CheckoutPage() {
  const { user } = useAuth()
  const userId = user?.id ?? ''
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [selectedAddressId, setSelectedAddressId] = useState<string>('')
  const [showAddressForm, setShowAddressForm] = useState(false)
  const [outcomeMessage, setOutcomeMessage] = useState<string | null>(null)
  const [unknownOutcome, setUnknownOutcome] = useState(false)

  /** Stable per logical attempt — reused on intentional retry of the same attempt. */
  const idempotencyKeyRef = useRef(newIdempotencyKey())

  const cartQuery = useQuery({
    queryKey: cartQueryKeys.detail(userId),
    queryFn: ({ signal }) => cartApi.getCart(signal),
    enabled: Boolean(userId),
    retry: false,
  })

  const addressesQuery = useQuery({
    queryKey: addressQueryKeys.list(userId),
    queryFn: ({ signal }) => addressApi.listAddresses(signal),
    enabled: Boolean(userId),
    retry: false,
  })

  const addressForm = useForm<AddressCreateFormValues>({
    resolver: zodResolver(addressCreateSchema),
    defaultValues: {
      label: '',
      recipientName: '',
      line1: '',
      line2: '',
      city: '',
      stateRegion: '',
      postalCode: '',
      countryCode: 'VN',
      phone: '',
      defaultAddress: true,
    },
  })

  const createAddressMutation = useMutation({
    mutationFn: (payload: Parameters<typeof addressApi.createAddress>[0]) =>
      addressApi.createAddress(payload),
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: addressQueryKeys.list(userId) })
      setSelectedAddressId(created.id)
      setShowAddressForm(false)
      addressForm.reset()
    },
  })

  const checkoutMutation = useMutation({
    mutationFn: (payload: Parameters<typeof checkoutApi.checkout>[0]) =>
      checkoutApi.checkout(payload),
    onSuccess: async (result: CheckoutResult) => {
      setUnknownOutcome(false)
      setOutcomeMessage(null)
      await queryClient.invalidateQueries({ queryKey: cartQueryKeys.detail(userId) })
      await queryClient.invalidateQueries({ queryKey: orderQueryKeys.lists(userId) })
      await queryClient.invalidateQueries({ queryKey: ['notifications', userId] })
      if (result.cartCleared || result.orderStatus === 'PAID') {
        void navigate(`/orders/${result.orderId}`, {
          replace: true,
          state: { checkoutResult: result },
        })
        return
      }
      // Mock payment can return 201 with FAILED — still a real order, not a silent success.
      void navigate(`/orders/${result.orderId}`, {
        replace: true,
        state: { checkoutResult: result },
      })
    },
    onError: (error) => {
      if (error instanceof ApiClientError) {
        setUnknownOutcome(false)
        setOutcomeMessage(error.userMessage)
        return
      }
      // Network / abort / unknown — do not invent success; keep same idempotency key.
      setUnknownOutcome(true)
      setOutcomeMessage(
        'Checkout outcome is unknown (network error). Do not assume failure or success. Check Orders, or retry with the same attempt key.',
      )
    },
  })

  const cart = cartQuery.data
  const addresses = addressesQuery.data ?? []
  const effectiveAddressId =
    selectedAddressId ||
    addresses.find((a) => a.defaultAddress)?.id ||
    addresses[0]?.id ||
    ''

  const canSubmit =
    cart != null &&
    cart.items.length > 0 &&
    !cart.hasUnavailableItems &&
    Boolean(effectiveAddressId) &&
    !checkoutMutation.isPending

  const startNewAttempt = () => {
    idempotencyKeyRef.current = newIdempotencyKey()
    setUnknownOutcome(false)
    setOutcomeMessage(null)
    checkoutMutation.reset()
  }

  const submitLockRef = useRef(false)

  const submitCheckout = () => {
    if (!canSubmit || !effectiveAddressId || submitLockRef.current) {
      return
    }
    submitLockRef.current = true
    setOutcomeMessage(null)
    checkoutMutation.mutate(
      {
        idempotencyKey: idempotencyKeyRef.current,
        addressId: effectiveAddressId,
      },
      {
        onSettled: () => {
          submitLockRef.current = false
        },
      },
    )
  }

  return (
    <section className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold tracking-tight text-foreground">Checkout</h1>
        <p className="text-sm text-muted-foreground">
          Review your cart. The server revalidates prices and stock, then runs a mock payment (demo
          only — not a real card charge). Totals below are informational until checkout succeeds.
        </p>
      </div>

      {cartQuery.isLoading || addressesQuery.isLoading ? (
        <p className="text-sm text-muted-foreground" role="status">
          Loading checkout…
        </p>
      ) : cartQuery.isError ? (
        <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">
          {cartQuery.error instanceof ApiClientError
            ? cartQuery.error.userMessage
            : 'Unable to load cart.'}
        </p>
      ) : !cart || cart.items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-card px-6 py-10 text-center">
          <p className="text-sm text-muted-foreground">Nothing to checkout.</p>
          <Link to="/cart" className="mt-4 inline-flex text-sm font-medium text-primary underline-offset-4 hover:underline">
            Back to cart
          </Link>
        </div>
      ) : (
        <>
          {cart.hasUnavailableItems ? (
            <p className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900" role="status">
              Remove unavailable items from the cart before placing an order.
            </p>
          ) : null}

          <div className="rounded-2xl border border-border bg-card p-4">
            <h2 className="text-sm font-semibold text-foreground">Order review</h2>
            <ul className="mt-3 divide-y divide-border">
              {cart.items.map((item) => (
                <li key={item.id} className="flex justify-between gap-4 py-2 text-sm">
                  <span className="text-foreground">
                    {item.productName} × {item.quantity}
                    {!item.available ? ' (unavailable)' : ''}
                  </span>
                  <span className="shrink-0 font-medium">
                    {formatProductPrice(item.lineSubtotal, item.currency)}
                  </span>
                </li>
              ))}
            </ul>
            <p className="mt-3 text-right text-base font-semibold text-foreground">
              Cart subtotal {formatProductPrice(cart.subtotal, cart.currency)}
            </p>
          </div>

          <div className="space-y-3 rounded-2xl border border-border bg-card p-4">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-sm font-semibold text-foreground">Shipping address</h2>
              <button
                type="button"
                className="text-sm font-medium text-primary underline-offset-4 hover:underline"
                onClick={() => setShowAddressForm((v) => !v)}
              >
                {showAddressForm ? 'Cancel' : 'Add address'}
              </button>
            </div>

            {addressesQuery.isError ? (
              <p className="text-sm text-red-700" role="alert">
                {addressesQuery.error instanceof ApiClientError
                  ? addressesQuery.error.userMessage
                  : 'Unable to load addresses.'}
              </p>
            ) : null}

            {addresses.length === 0 && !showAddressForm ? (
              <p className="text-sm text-muted-foreground">
                Add a shipping address to continue. Checkout requires a server-side{' '}
                <code className="text-xs">addressId</code>.
              </p>
            ) : null}

            {addresses.length > 0 ? (
              <fieldset className="space-y-2">
                <legend className="sr-only">Choose shipping address</legend>
                {addresses.map((address) => (
                  <label
                    key={address.id}
                    className="flex cursor-pointer gap-3 rounded-lg border border-border px-3 py-2 text-sm hover:bg-muted"
                  >
                    <input
                      type="radio"
                      name="addressId"
                      value={address.id}
                      checked={effectiveAddressId === address.id}
                      onChange={() => setSelectedAddressId(address.id)}
                      disabled={checkoutMutation.isPending}
                    />
                    <span>
                      <span className="font-medium text-foreground">{address.recipientName}</span>
                      {address.defaultAddress ? (
                        <span className="ml-2 text-xs text-muted-foreground">Default</span>
                      ) : null}
                      <br />
                      <span className="text-muted-foreground">
                        {address.line1}
                        {address.line2 ? `, ${address.line2}` : ''}, {address.city}
                        {address.stateRegion ? `, ${address.stateRegion}` : ''}{' '}
                        {address.postalCode ?? ''} {address.countryCode}
                      </span>
                    </span>
                  </label>
                ))}
              </fieldset>
            ) : null}

            {showAddressForm ? (
              <form
                className="grid gap-3 border-t border-border pt-4 sm:grid-cols-2"
                onSubmit={addressForm.handleSubmit(async (values) => {
                  await createAddressMutation.mutateAsync({
                    label: values.label || undefined,
                    recipientName: values.recipientName,
                    line1: values.line1,
                    line2: values.line2 || undefined,
                    city: values.city,
                    stateRegion: values.stateRegion || undefined,
                    postalCode: values.postalCode || undefined,
                    countryCode: values.countryCode || 'VN',
                    phone: values.phone || undefined,
                    defaultAddress: values.defaultAddress ?? true,
                  })
                })}
                noValidate
              >
                {(
                  [
                    ['recipientName', 'Recipient name', true],
                    ['line1', 'Address line 1', true],
                    ['line2', 'Address line 2', false],
                    ['city', 'City', true],
                    ['stateRegion', 'State / region', false],
                    ['postalCode', 'Postal code', false],
                    ['countryCode', 'Country (ISO-2)', false],
                    ['phone', 'Phone', false],
                    ['label', 'Label', false],
                  ] as const
                ).map(([name, label, required]) => (
                  <label key={name} className="block space-y-1 text-sm sm:col-span-1">
                    <span className="text-muted-foreground">
                      {label}
                      {required ? ' *' : ''}
                    </span>
                    <input
                      className="w-full rounded-lg border border-border bg-background px-3 py-2"
                      disabled={createAddressMutation.isPending}
                      {...addressForm.register(name)}
                    />
                    {addressForm.formState.errors[name] ? (
                      <span className="text-xs text-red-700">
                        {addressForm.formState.errors[name]?.message}
                      </span>
                    ) : null}
                  </label>
                ))}
                {createAddressMutation.isError ? (
                  <p className="sm:col-span-2 text-sm text-red-700" role="alert">
                    {createAddressMutation.error instanceof ApiClientError
                      ? createAddressMutation.error.userMessage
                      : 'Could not save address.'}
                  </p>
                ) : null}
                <button
                  type="submit"
                  disabled={createAddressMutation.isPending}
                  className="sm:col-span-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-60"
                >
                  {createAddressMutation.isPending ? 'Saving…' : 'Save address'}
                </button>
              </form>
            ) : null}
          </div>

          {outcomeMessage ? (
            <div
              className={`rounded-lg px-4 py-3 text-sm ${unknownOutcome ? 'border border-amber-200 bg-amber-50 text-amber-950' : 'bg-red-50 text-red-700'}`}
              role="alert"
            >
              <p>{outcomeMessage}</p>
              {unknownOutcome ? (
                <div className="mt-3 flex flex-wrap gap-2">
                  <Link
                    to="/orders"
                    className="rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium"
                  >
                    View orders
                  </Link>
                  <button
                    type="button"
                    className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground"
                    disabled={checkoutMutation.isPending}
                    onClick={() => submitCheckout()}
                  >
                    Retry same attempt
                  </button>
                  <button
                    type="button"
                    className="rounded-lg border border-border px-3 py-1.5 text-sm font-medium"
                    onClick={startNewAttempt}
                  >
                    Start new attempt
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  className="mt-3 rounded-lg border border-border bg-card px-3 py-1.5 text-sm font-medium"
                  onClick={startNewAttempt}
                >
                  Dismiss and start new attempt
                </button>
              )}
            </div>
          ) : null}

          <div className="flex flex-wrap gap-2">
            <Link
              to="/cart"
              className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-muted"
            >
              Back to cart
            </Link>
            <button
              type="button"
              disabled={!canSubmit}
              className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:cursor-not-allowed disabled:opacity-60"
              onClick={() => submitCheckout()}
            >
              {checkoutMutation.isPending ? 'Placing order…' : 'Place order (mock payment)'}
            </button>
          </div>
        </>
      )}
    </section>
  )
}
