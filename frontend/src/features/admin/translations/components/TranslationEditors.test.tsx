import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { clearSession } from '@/features/auth/session/session-coordinator'
import { demoAdminSession, renderWithProviders } from '@/test/render-app'

import { EntityTranslationsEditor } from './EntityTranslationsEditor'
import { NameTranslationsEditor } from './NameTranslationsEditor'
import { PlacementTranslationsEditor } from './PlacementTranslationsEditor'

const ID = '33333333-3333-4333-8333-333333333333'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

type Call = { url: string; method: string; body: unknown }

/** Routes the T04 translation endpoints; everything else is 404. */
function mockApi(routes: Record<string, (call: Call) => Response>) {
  const calls: Call[] = []
  vi.mocked(fetch).mockImplementation(async (input, init) => {
    const url = String(input)
    if (url.includes('/auth/refresh')) {
      return jsonResponse(200, demoAdminSession)
    }
    const call: Call = {
      url,
      method: init?.method ?? 'GET',
      body: init?.body ? JSON.parse(String(init.body)) : undefined,
    }
    const key = Object.keys(routes).find((path) => url.includes(path))
    if (!key) {
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    }
    calls.push(call)
    return routes[key](call)
  })
  return calls
}

describe('Admin translation editors', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  describe('EntityTranslationsEditor', () => {
    const path = `/admin/products/${ID}/translations`
    const loaded = {
      id: ID,
      translations: {
        vi: { name: 'Điện thoại', description: 'Mô tả' },
        en: { name: null, description: null },
      },
    }

    it('loads VI + EN values and saves EN via PUT, then invalidates dependent queries', async () => {
      const user = userEvent.setup()
      const calls = mockApi({
        [path]: (call) =>
          call.method === 'PUT'
            ? jsonResponse(200, {
                id: ID,
                translations: {
                  vi: { name: 'Điện thoại', description: 'Mô tả' },
                  en: { name: 'Phone', description: 'Description' },
                },
              })
            : jsonResponse(200, loaded),
      })

      const { queryClient } = renderWithProviders(
        <EntityTranslationsEditor kind="product" entityId={ID} />,
        { session: demoAdminSession },
      )
      const invalidate = vi.spyOn(queryClient, 'invalidateQueries')

      expect(await screen.findByLabelText('Name (VI)')).toHaveValue('Điện thoại')
      expect(screen.getByLabelText('Description (VI)')).toHaveValue('Mô tả')
      expect(screen.getByLabelText('Name (EN)')).toHaveValue('')

      await user.type(screen.getByLabelText('Name (EN)'), 'Phone')
      await user.type(screen.getByLabelText('Description (EN)'), 'Description')
      await user.click(screen.getByRole('button', { name: /save translations/i }))

      expect(await screen.findByText('Translations saved.')).toBeInTheDocument()
      const put = calls.find((c) => c.method === 'PUT')
      expect(put?.url).toContain(path)
      expect(put?.body).toEqual({
        translations: {
          vi: { name: 'Điện thoại', description: 'Mô tả' },
          en: { name: 'Phone', description: 'Description' },
        },
      })
      await waitFor(() => expect(invalidate).toHaveBeenCalled())
    })

    it('omits EN from the payload when the EN name is blank', async () => {
      const user = userEvent.setup()
      const calls = mockApi({
        [path]: () => jsonResponse(200, loaded),
      })

      renderWithProviders(<EntityTranslationsEditor kind="product" entityId={ID} />, {
        session: demoAdminSession,
      })

      await screen.findByLabelText('Name (VI)')
      await user.click(screen.getByRole('button', { name: /save translations/i }))

      await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
      expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
        translations: { vi: { name: 'Điện thoại', description: 'Mô tả' } },
      })
    })

    it('requires the VI name and does not call PUT when it is empty', async () => {
      const user = userEvent.setup()
      const calls = mockApi({ [path]: () => jsonResponse(200, loaded) })

      renderWithProviders(<EntityTranslationsEditor kind="product" entityId={ID} />, {
        session: demoAdminSession,
      })

      await user.clear(await screen.findByLabelText('Name (VI)'))
      await user.click(screen.getByRole('button', { name: /save translations/i }))

      expect(await screen.findByText(/vietnamese \(vi\) value is required/i)).toBeInTheDocument()
      expect(calls.some((c) => c.method === 'PUT')).toBe(false)
    })

    it('requires an EN name when an EN description is provided', async () => {
      const user = userEvent.setup()
      const calls = mockApi({ [path]: () => jsonResponse(200, loaded) })

      renderWithProviders(<EntityTranslationsEditor kind="product" entityId={ID} />, {
        session: demoAdminSession,
      })

      await user.type(await screen.findByLabelText('Description (EN)'), 'Only description')
      await user.click(screen.getByRole('button', { name: /save translations/i }))

      expect(await screen.findByText(/english name is required/i)).toBeInTheDocument()
      expect(calls.some((c) => c.method === 'PUT')).toBe(false)
    })

    it('uses the categories endpoint for kind=category', async () => {
      const calls = mockApi({
        [`/admin/categories/${ID}/translations`]: () => jsonResponse(200, loaded),
      })

      renderWithProviders(<EntityTranslationsEditor kind="category" entityId={ID} />, {
        session: demoAdminSession,
      })

      expect(await screen.findByLabelText('Name (VI)')).toBeInTheDocument()
      expect(screen.getByRole('heading', { name: /category translations/i })).toBeInTheDocument()
      expect(calls[0]?.url).toContain(`/admin/categories/${ID}/translations`)
    })

    it('shows a load error with reload, and surfaces PUT API errors', async () => {
      const user = userEvent.setup()
      let fail = true
      mockApi({
        [path]: (call) => {
          if (call.method === 'PUT') {
            return jsonResponse(400, {
              status: 400,
              error: 'Bad Request',
              message: 'translations.vi.name must not be blank',
            })
          }
          return fail
            ? jsonResponse(500, { status: 500, error: 'Server Error', message: 'boom' })
            : jsonResponse(200, loaded)
        },
      })

      renderWithProviders(<EntityTranslationsEditor kind="product" entityId={ID} />, {
        session: demoAdminSession,
      })

      expect(await screen.findByRole('alert')).toBeInTheDocument()
      fail = false
      await user.click(screen.getByRole('button', { name: /reload/i }))

      await screen.findByLabelText('Name (VI)')
      await user.click(screen.getByRole('button', { name: /save translations/i }))
      expect(await screen.findByRole('alert')).toBeInTheDocument()
      expect(screen.queryByText('Translations saved.')).not.toBeInTheDocument()
    })
  })

  describe('NameTranslationsEditor', () => {
    it.each([
      ['strategy', `/admin/promotions/strategies/${ID}/translations`],
      ['campaign', `/admin/promotions/campaigns/${ID}/translations`],
    ] as const)('loads and saves %s names', async (kind, path) => {
      const user = userEvent.setup()
      const calls = mockApi({
        [path]: (call) =>
          jsonResponse(200, {
            id: ID,
            translations: { vi: 'Khuyến mãi hè', en: call.method === 'PUT' ? 'Summer sale' : null },
          }),
      })

      renderWithProviders(<NameTranslationsEditor kind={kind} entityId={ID} />, {
        session: demoAdminSession,
      })

      expect(await screen.findByLabelText('Name (VI)')).toHaveValue('Khuyến mãi hè')
      await user.type(screen.getByLabelText('Name (EN)'), 'Summer sale')
      await user.click(screen.getByRole('button', { name: /save translations/i }))

      expect(await screen.findByText('Translations saved.')).toBeInTheDocument()
      expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
        translations: { vi: 'Khuyến mãi hè', en: 'Summer sale' },
      })
    })
  })

  describe('PlacementTranslationsEditor', () => {
    const path = `/admin/promotions/placements/${ID}/translations`
    const loaded = {
      id: ID,
      translations: {
        vi: { title: 'Tiêu đề', subtitle: 'Phụ đề', ctaLabel: 'Mua ngay' },
        en: { title: null, subtitle: null, ctaLabel: null },
      },
    }

    it('loads and saves title/subtitle/CTA label for EN', async () => {
      const user = userEvent.setup()
      const calls = mockApi({ [path]: () => jsonResponse(200, loaded) })

      renderWithProviders(<PlacementTranslationsEditor placementId={ID} />, {
        session: demoAdminSession,
      })

      expect(await screen.findByLabelText('Title (VI)')).toHaveValue('Tiêu đề')
      expect(screen.getByLabelText('Subtitle (VI)')).toHaveValue('Phụ đề')
      expect(screen.getByLabelText('CTA label (VI)')).toHaveValue('Mua ngay')

      await user.type(screen.getByLabelText('Title (EN)'), 'Title')
      await user.type(screen.getByLabelText('CTA label (EN)'), 'Shop now')
      await user.click(screen.getByRole('button', { name: /save translations/i }))

      await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true))
      expect(calls.find((c) => c.method === 'PUT')?.body).toEqual({
        translations: {
          vi: { title: 'Tiêu đề', subtitle: 'Phụ đề', ctaLabel: 'Mua ngay' },
          en: { title: 'Title', subtitle: null, ctaLabel: 'Shop now' },
        },
      })
    })

    it('requires an EN title when an EN subtitle or CTA label is set', async () => {
      const user = userEvent.setup()
      const calls = mockApi({ [path]: () => jsonResponse(200, loaded) })

      renderWithProviders(<PlacementTranslationsEditor placementId={ID} />, {
        session: demoAdminSession,
      })

      await user.type(await screen.findByLabelText('Subtitle (EN)'), 'Only subtitle')
      await user.click(screen.getByRole('button', { name: /save translations/i }))

      expect(await screen.findByText(/english title is required/i)).toBeInTheDocument()
      expect(calls.some((c) => c.method === 'PUT')).toBe(false)
    })
  })

  it('localizes editor chrome in Vietnamese', async () => {
    const { changeAppLocale } = await import('@/lib/i18n')
    await changeAppLocale('vi')
    mockApi({
      [`/admin/products/${ID}/translations`]: () =>
        jsonResponse(200, {
          id: ID,
          translations: {
            vi: { name: 'A', description: null },
            en: { name: null, description: null },
          },
        }),
    })

    renderWithProviders(<EntityTranslationsEditor kind="product" entityId={ID} />, {
      session: demoAdminSession,
    })

    expect(await screen.findByRole('button', { name: /lưu bản dịch/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /bản dịch sản phẩm/i })).toBeInTheDocument()
  })
})
