import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/features/auth/hooks/useAuth'
import * as cartApi from '@/features/cart/api/cart-api'
import { cartQueryKeys } from '@/features/cart/hooks/cart-query-keys'
import { ProductPrice } from '@/features/catalog/components/ProductPrice'
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
        <CheckoutLoadingSkeleton />
      ) : cartQuery.isError ? (
        <p
          className="rounded-xl border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive"
          role="alert"
        >
          {cartQuery.error instanceof ApiClientError
            ? cartQuery.error.userMessage
            : 'Unable to load cart.'}
        </p>
      ) : !cart || cart.items.length === 0 ? (
        <Card className="border-dashed shadow-none">
          <CardContent className="flex flex-col items-center gap-4 px-6 py-12 text-center">
            <p className="text-sm text-muted-foreground">Nothing to checkout.</p>
            <Button asChild variant="link">
              <Link to="/cart">Back to cart</Link>
            </Button>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)] lg:items-start">
          <div className="space-y-4">
            {cart.hasUnavailableItems ? (
              <p
                className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900"
                role="status"
              >
                Remove unavailable items from the cart before placing an order.
              </p>
            ) : null}

            <Card className="shadow-sm">
              <CardHeader>
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="space-y-1.5">
                    <CardTitle>Shipping address</CardTitle>
                    <CardDescription>Required before placing an order.</CardDescription>
                  </div>
                  <Button
                    type="button"
                    variant="link"
                    className="h-auto px-0"
                    onClick={() => setShowAddressForm((v) => !v)}
                  >
                    {showAddressForm ? 'Cancel' : 'Add address'}
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                {addressesQuery.isError ? (
                  <p className="text-sm text-destructive" role="alert">
                    {addressesQuery.error instanceof ApiClientError
                      ? addressesQuery.error.userMessage
                      : 'Unable to load addresses.'}
                  </p>
                ) : null}

                {addresses.length === 0 && !showAddressForm ? (
                  <p className="text-sm text-muted-foreground">
                    Add a shipping address to continue. Checkout requires a saved address owned by
                    your account.
                  </p>
                ) : null}

                {addresses.length > 0 ? (
                  <fieldset className="space-y-2">
                    <legend className="sr-only">Choose shipping address</legend>
                    {addresses.map((address) => (
                      <label
                        key={address.id}
                        className="flex cursor-pointer gap-3 rounded-xl border border-border px-3 py-3 text-sm transition hover:border-primary/40 has-[:checked]:border-primary has-[:checked]:bg-primary/5"
                      >
                        <input
                          type="radio"
                          name="addressId"
                          value={address.id}
                          checked={effectiveAddressId === address.id}
                          onChange={() => setSelectedAddressId(address.id)}
                          disabled={checkoutMutation.isPending}
                          className="mt-1"
                        />
                        <span>
                          <span className="font-medium text-foreground">{address.recipientName}</span>
                          {address.defaultAddress ? (
                            <Badge variant="secondary" className="ml-2 align-middle">
                              Default
                            </Badge>
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
                      <div key={name} className="space-y-1.5 sm:col-span-1">
                        <Label htmlFor={`checkout-address-${name}`}>
                          {label}
                          {required ? ' *' : ''}
                        </Label>
                        <Input
                          id={`checkout-address-${name}`}
                          disabled={createAddressMutation.isPending}
                          aria-invalid={Boolean(addressForm.formState.errors[name])}
                          {...addressForm.register(name)}
                        />
                        {addressForm.formState.errors[name] ? (
                          <span className="text-xs text-destructive">
                            {addressForm.formState.errors[name]?.message}
                          </span>
                        ) : null}
                      </div>
                    ))}
                    {createAddressMutation.isError ? (
                      <p className="sm:col-span-2 text-sm text-destructive" role="alert">
                        {createAddressMutation.error instanceof ApiClientError
                          ? createAddressMutation.error.userMessage
                          : 'Could not save address.'}
                      </p>
                    ) : null}
                    <Button
                      type="submit"
                      disabled={createAddressMutation.isPending}
                      className="sm:col-span-2"
                    >
                      {createAddressMutation.isPending ? 'Saving…' : 'Save address'}
                    </Button>
                  </form>
                ) : null}
              </CardContent>
            </Card>

            {outcomeMessage ? (
              <div
                className={`rounded-xl px-4 py-3 text-sm ${
                  unknownOutcome
                    ? 'border border-amber-200 bg-amber-50 text-amber-950'
                    : 'border border-destructive/30 bg-destructive/5 text-destructive'
                }`}
                role="alert"
              >
                <p>{outcomeMessage}</p>
                {unknownOutcome ? (
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Button asChild variant="outline" size="sm">
                      <Link to="/orders">View orders</Link>
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      disabled={checkoutMutation.isPending}
                      onClick={() => submitCheckout()}
                    >
                      Retry same attempt
                    </Button>
                    <Button type="button" variant="outline" size="sm" onClick={startNewAttempt}>
                      Start new attempt
                    </Button>
                  </div>
                ) : (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="mt-3"
                    onClick={startNewAttempt}
                  >
                    Dismiss and start new attempt
                  </Button>
                )}
              </div>
            ) : null}
          </div>

          <Card className="sticky top-20 shadow-sm">
            <CardHeader>
              <CardTitle>Order review</CardTitle>
              <CardDescription>Lines and subtotal from your current cart.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <ul className="divide-y divide-border">
                {cart.items.map((item) => (
                  <li key={item.id} className="flex justify-between gap-4 py-3 text-sm">
                    <span className="text-foreground">
                      {item.productName} × {item.quantity}
                      {!item.available ? ' (unavailable)' : ''}
                    </span>
                    <ProductPrice
                      unitPrice={item.lineSubtotal}
                      currency={item.currency}
                      className="shrink-0 text-sm font-medium text-foreground"
                    />
                  </li>
                ))}
              </ul>
              <Separator />
              <div className="flex items-center justify-between gap-3">
                <span className="text-sm text-muted-foreground">Cart subtotal</span>
                <ProductPrice
                  unitPrice={cart.subtotal}
                  currency={cart.currency}
                  className="text-base font-semibold text-foreground"
                />
              </div>
              <p className="text-xs text-muted-foreground">
                Demo checkout uses mock payment only. Shipping is confirmed by the server (demo
                shipping is zero).
              </p>
              <div className="flex flex-col gap-2 pt-2">
                <Button
                  type="button"
                  size="lg"
                  disabled={!canSubmit}
                  onClick={() => submitCheckout()}
                >
                  {checkoutMutation.isPending ? 'Placing order…' : 'Place order (mock payment)'}
                </Button>
                <Button asChild variant="outline">
                  <Link to="/cart">Back to cart</Link>
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </section>
  )
}

function CheckoutLoadingSkeleton() {
  return (
    <div
      className="grid gap-6 lg:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]"
      role="status"
      aria-live="polite"
    >
      <p className="sr-only">Loading checkout…</p>
      <Skeleton className="h-64 w-full rounded-xl" />
      <Skeleton className="h-72 w-full rounded-xl" />
    </div>
  )
}
