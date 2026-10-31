import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { AppRouter } from '@/app/router'
import { clearSession } from '@/features/auth/session/session-coordinator'
import { demoAdminSession, demoCustomerSession, renderWithProviders } from '@/test/render-app'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const category = {
  id: '11111111-1111-4111-8111-111111111111',
  name: 'Electronics',
  slug: 'electronics',
  description: null,
  parentId: null,
  active: true,
}

const product = {
  id: '22222222-2222-4222-8222-222222222222',
  sku: 'DEMO-PHONE',
  name: 'Demo Phone',
  description: 'Flagship',
  unitPrice: 1000000,
  currency: 'VND',
  categoryId: category.id,
  categoryName: category.name,
  active: true,
  imageUrl: null,
  inStock: true,
}

function mockAdminFetch(
  handler: (url: string, init?: RequestInit) => Response | Promise<Response>,
) {
  vi.mocked(fetch).mockImplementation(async (input, init) => {
    const url = String(input)
    if (url.includes('/auth/refresh')) {
      return jsonResponse(200, demoAdminSession)
    }
    return handler(url, init)
  })
}

describe('Admin product pages', () => {
  beforeEach(() => {
    clearSession()
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    clearSession()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('blocks CUSTOMER from product admin content', async () => {
    vi.mocked(fetch).mockImplementation(async (input) => {
      const url = String(input)
      if (url.includes('/auth/refresh')) {
        return jsonResponse(200, demoCustomerSession)
      }
      return jsonResponse(403, { status: 403, error: 'Forbidden', message: 'Forbidden' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products',
      session: demoCustomerSession,
    })

    expect(await screen.findByRole('heading', { name: /access denied/i })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /^products$/i })).not.toBeInTheDocument()
  })

  it('renders product list from the admin API', async () => {
    mockAdminFetch((url) => {
      if (url.includes('/admin/products')) {
        return jsonResponse(200, {
          items: [product],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        })
      }
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products',
      session: demoAdminSession,
    })

    expect(await screen.findByRole('heading', { name: /^products$/i })).toBeInTheDocument()
    expect(await screen.findByText(/demo phone/i)).toBeInTheDocument()
    expect(screen.getByText(/demo-phone/i)).toBeInTheDocument()
    expect(screen.getByText(/1 total/i)).toBeInTheDocument()
  })

  it('shows empty product list state', async () => {
    mockAdminFetch((url) => {
      if (url.includes('/admin/products')) {
        return jsonResponse(200, {
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
          first: true,
          last: true,
        })
      }
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products',
      session: demoAdminSession,
    })

    expect(await screen.findByText(/no products match/i)).toBeInTheDocument()
  })

  it('validates required fields on create and posts the supported payload', async () => {
    const user = userEvent.setup()
    const creates: unknown[] = []

    mockAdminFetch((url, init) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      if (url.endsWith('/admin/products') && init?.method === 'POST') {
        creates.push(JSON.parse(String(init.body)))
        return jsonResponse(201, product)
      }
      if (url.includes(`/admin/products/${product.id}/inventory`)) {
        return jsonResponse(200, {
          productId: product.id,
          quantityOnHand: 0,
          quantityReserved: 0,
          available: 0,
          updatedAt: null,
        })
      }
      if (url.includes(`/admin/products/${product.id}`)) {
        return jsonResponse(200, product)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products/new',
      session: demoAdminSession,
    })

    await screen.findByRole('heading', { name: /create product/i })
    await screen.findByRole('option', { name: /electronics/i })
    await user.click(screen.getByRole('button', { name: /create product/i }))
    expect(await screen.findByText(/sku is required/i)).toBeInTheDocument()
    expect(creates).toHaveLength(0)

    await user.type(screen.getByLabelText(/^sku$/i), 'DEMO-PHONE')
    await user.type(screen.getByLabelText(/^name$/i), 'Demo Phone')
    await user.clear(screen.getByLabelText(/^unit price$/i))
    await user.type(screen.getByLabelText(/^unit price$/i), '1000000')
    await user.selectOptions(screen.getByLabelText(/^category$/i), category.id)
    await user.click(screen.getByRole('button', { name: /create product/i }))

    await waitFor(() => expect(creates).toHaveLength(1))
    expect(creates[0]).toEqual({
      sku: 'DEMO-PHONE',
      name: 'Demo Phone',
      description: null,
      unitPrice: 1000000,
      currency: 'VND',
      categoryId: category.id,
      imageUrl: null,
    })
    expect(JSON.stringify(creates[0])).not.toMatch(/onHand|reserved|available|quantity/i)
    expect(JSON.stringify(creates[0])).not.toMatch(/base64|data:image/i)
    expect(await screen.findByRole('heading', { name: /edit product/i })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: /^inventory$/i })).toBeInTheDocument()
  })

  it('uploads an image and includes the returned path on create', async () => {
    const user = userEvent.setup()
    const creates: unknown[] = []
    const uploadedPath = '/uploads/products/9d1f7e4b-1111-4111-8111-111111111111.png'

    mockAdminFetch((url, init) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      if (url.includes('/admin/uploads/images') && init?.method === 'POST') {
        expect(init.body).toBeInstanceOf(FormData)
        const formData = init.body as FormData
        expect(formData.get('file')).toBeInstanceOf(File)
        expect(formData.get('image')).toBeNull()
        // Browser sets multipart boundary — client must not force Content-Type.
        const headers = init.headers as Headers | Record<string, string> | undefined
        const contentType =
          headers instanceof Headers
            ? headers.get('Content-Type')
            : headers?.['Content-Type'] ?? headers?.['content-type']
        expect(contentType ?? '').not.toMatch(/^multipart\/form-data;?$/)
        return jsonResponse(200, {
          imageUrl: uploadedPath,
          contentType: 'image/png',
          sizeBytes: 12,
        })
      }
      if (url.endsWith('/admin/products') && init?.method === 'POST') {
        creates.push(JSON.parse(String(init.body)))
        return jsonResponse(201, { ...product, imageUrl: uploadedPath })
      }
      if (url.includes(`/admin/products/${product.id}/inventory`)) {
        return jsonResponse(200, {
          productId: product.id,
          quantityOnHand: 0,
          quantityReserved: 0,
          available: 0,
          updatedAt: null,
        })
      }
      if (url.includes(`/admin/products/${product.id}`)) {
        return jsonResponse(200, { ...product, imageUrl: uploadedPath })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products/new',
      session: demoAdminSession,
    })

    await screen.findByRole('option', { name: /electronics/i })
    const file = new File([new Uint8Array([1, 2, 3, 4])], 'apple.png', { type: 'image/png' })
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement
    expect(fileInput).toBeTruthy()
    await user.upload(fileInput, file)

    expect(await screen.findByText(/image uploaded/i)).toBeInTheDocument()
    expect(screen.getByText(uploadedPath)).toBeInTheDocument()

    await user.type(screen.getByLabelText(/^sku$/i), 'DEMO-PHONE')
    await user.type(screen.getByLabelText(/^name$/i), 'Demo Phone')
    await user.clear(screen.getByLabelText(/^unit price$/i))
    await user.type(screen.getByLabelText(/^unit price$/i), '1000000')
    await user.selectOptions(screen.getByLabelText(/^category$/i), category.id)
    await user.click(screen.getByRole('button', { name: /create product/i }))

    await waitFor(() => expect(creates).toHaveLength(1))
    expect(creates[0]).toMatchObject({ imageUrl: uploadedPath })
  })

  it('rejects oversized image before calling upload API', async () => {
    const user = userEvent.setup()
    let uploadCalls = 0

    mockAdminFetch((url) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      if (url.includes('/admin/uploads/images')) {
        uploadCalls += 1
        return jsonResponse(200, { imageUrl: '/uploads/products/x.png', contentType: 'image/png', sizeBytes: 1 })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products/new',
      session: demoAdminSession,
    })

    await screen.findByRole('heading', { name: /create product/i })
    const file = new File([new Uint8Array(2 * 1024 * 1024)], 'big.png', { type: 'image/png' })
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(fileInput, file)

    expect(await screen.findByRole('alert')).toHaveTextContent(/smaller than/i)
    expect(uploadCalls).toBe(0)
  })

  it('rejects unsupported gif before calling upload API', async () => {
    let uploadCalls = 0

    mockAdminFetch((url) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      if (url.includes('/admin/uploads/images')) {
        uploadCalls += 1
        return jsonResponse(200, { imageUrl: '/uploads/products/x.png', contentType: 'image/png', sizeBytes: 1 })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products/new',
      session: demoAdminSession,
    })

    await screen.findByRole('heading', { name: /create product/i })
    const file = new File([new Uint8Array([1, 2, 3])], 'x.gif', { type: 'image/gif' })
    fireEvent.change(document.querySelector('input[type="file"]') as HTMLInputElement, {
      target: { files: [file] },
    })

    expect(await screen.findByRole('alert')).toHaveTextContent(/unsupported/i)
    expect(uploadCalls).toBe(0)
  })

  it('keeps form imageUrl unchanged when upload API fails', async () => {
    const user = userEvent.setup()
    const creates: unknown[] = []

    mockAdminFetch((url, init) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      if (url.includes('/admin/uploads/images') && init?.method === 'POST') {
        return jsonResponse(400, {
          status: 400,
          error: 'Bad Request',
          message: 'Unsupported image format',
        })
      }
      if (url.endsWith('/admin/products') && init?.method === 'POST') {
        creates.push(JSON.parse(String(init.body)))
        return jsonResponse(201, product)
      }
      if (url.includes(`/admin/products/${product.id}/inventory`)) {
        return jsonResponse(200, {
          productId: product.id,
          quantityOnHand: 0,
          quantityReserved: 0,
          available: 0,
          updatedAt: null,
        })
      }
      if (url.includes(`/admin/products/${product.id}`)) {
        return jsonResponse(200, product)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products/new',
      session: demoAdminSession,
    })

    await screen.findByRole('option', { name: /electronics/i })
    await user.upload(
      document.querySelector('input[type="file"]') as HTMLInputElement,
      new File([new Uint8Array([1, 2, 3])], 'bad.png', { type: 'image/png' }),
    )
    expect(await screen.findByRole('alert')).toHaveTextContent(/unsupported image format/i)

    await user.type(screen.getByLabelText(/^sku$/i), 'DEMO-PHONE')
    await user.type(screen.getByLabelText(/^name$/i), 'Demo Phone')
    await user.clear(screen.getByLabelText(/^unit price$/i))
    await user.type(screen.getByLabelText(/^unit price$/i), '1000000')
    await user.selectOptions(screen.getByLabelText(/^category$/i), category.id)
    await user.click(screen.getByRole('button', { name: /create product/i }))

    await waitFor(() => expect(creates).toHaveLength(1))
    expect(creates[0]).toMatchObject({ imageUrl: null })
  })

  it('shows duplicate SKU conflict from the API', async () => {
    const user = userEvent.setup()

    mockAdminFetch((url, init) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      if (url.endsWith('/admin/products') && init?.method === 'POST') {
        return jsonResponse(409, {
          status: 409,
          error: 'Conflict',
          message: 'SKU already exists',
          details: [],
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products/new',
      session: demoAdminSession,
    })

    await screen.findByRole('option', { name: /electronics/i })
    await user.type(screen.getByLabelText(/^sku$/i), 'DEMO-PHONE')
    await user.type(screen.getByLabelText(/^name$/i), 'Demo Phone')
    await user.clear(screen.getByLabelText(/^unit price$/i))
    await user.type(screen.getByLabelText(/^unit price$/i), '1000000')
    await user.selectOptions(screen.getByLabelText(/^category$/i), category.id)
    await user.click(screen.getByRole('button', { name: /create product/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/sku already exists/i)
    expect(screen.getByLabelText(/^sku$/i)).toHaveValue('DEMO-PHONE')
  })

  it('loads inventory on edit and updates stock via admin inventory API', async () => {
    const user = userEvent.setup()
    let inventory = {
      productId: product.id,
      quantityOnHand: 5,
      quantityReserved: 2,
      available: 3,
      updatedAt: '2026-01-01T00:00:00Z',
    }
    const inventoryPuts: unknown[] = []

    mockAdminFetch((url, init) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      if (url.includes(`/admin/products/${product.id}/inventory`)) {
        if (init?.method === 'PUT') {
          inventoryPuts.push(JSON.parse(String(init.body)))
          const body = inventoryPuts[inventoryPuts.length - 1] as { quantityOnHand: number }
          inventory = {
            ...inventory,
            quantityOnHand: body.quantityOnHand,
            available: body.quantityOnHand - inventory.quantityReserved,
          }
          return jsonResponse(200, inventory)
        }
        return jsonResponse(200, inventory)
      }
      if (url.includes(`/admin/products/${product.id}`)) {
        return jsonResponse(200, product)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: `/admin/products/${product.id}/edit`,
      session: demoAdminSession,
    })

    expect(await screen.findByLabelText(/stock quantity/i)).toHaveValue('5')
    expect(screen.getByText(/^on hand$/i)).toBeInTheDocument()
    expect(screen.getByText(/^reserved$/i)).toBeInTheDocument()
    expect(screen.getByText(/^available$/i)).toBeInTheDocument()

    const stockInput = screen.getByLabelText(/stock quantity/i)
    await user.clear(stockInput)
    await user.click(screen.getByRole('button', { name: /update stock/i }))
    expect(await screen.findByText(/stock quantity is required/i)).toBeInTheDocument()
    expect(inventoryPuts).toHaveLength(0)

    await user.type(screen.getByLabelText(/stock quantity/i), '8')
    await user.click(screen.getByRole('button', { name: /update stock/i }))

    await waitFor(() => expect(inventoryPuts).toEqual([{ quantityOnHand: 8 }]))
    expect(await screen.findByText(/stock quantity updated/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/stock quantity/i)).toHaveValue('8')
  })

  it('shows inventory API errors on the edit form', async () => {
    const user = userEvent.setup()

    mockAdminFetch((url, init) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      if (url.includes(`/admin/products/${product.id}/inventory`)) {
        if (init?.method === 'PUT') {
          return jsonResponse(400, {
            status: 400,
            error: 'Bad Request',
            message: 'quantityOnHand (1) cannot be less than quantityReserved (2)',
          })
        }
        return jsonResponse(200, {
          productId: product.id,
          quantityOnHand: 5,
          quantityReserved: 2,
          available: 3,
          updatedAt: null,
        })
      }
      if (url.includes(`/admin/products/${product.id}`)) {
        return jsonResponse(200, product)
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: `/admin/products/${product.id}/edit`,
      session: demoAdminSession,
    })

    await screen.findByLabelText(/stock quantity/i)
    await user.clear(screen.getByLabelText(/stock quantity/i))
    await user.type(screen.getByLabelText(/stock quantity/i), '1')
    await user.click(screen.getByRole('button', { name: /update stock/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/quantityReserved/i)
  })

  it('does not show inventory controls on create', async () => {
    mockAdminFetch((url) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products/new',
      session: demoAdminSession,
    })

    await screen.findByRole('heading', { name: /create product/i })
    expect(screen.queryByRole('heading', { name: /^inventory$/i })).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/stock quantity/i)).not.toBeInTheDocument()
  })

  it('loads edit form and patches status via soft-disable endpoint', async () => {
    const user = userEvent.setup()
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    let productState = { ...product }
    const statusBodies: unknown[] = []

    mockAdminFetch((url, init) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      if (url.includes('/admin/products') && url.includes('/status') && init?.method === 'PATCH') {
        statusBodies.push(JSON.parse(String(init.body)))
        productState = { ...productState, active: false }
        return jsonResponse(200, productState)
      }
      if (url.includes('/admin/products') && (!init?.method || init.method === 'GET')) {
        if (url.includes(product.id) && !url.includes('?')) {
          return jsonResponse(200, productState)
        }
        return jsonResponse(200, {
          items: [productState],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products',
      session: demoAdminSession,
    })

    expect(await screen.findByText(/demo phone/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /deactivate/i }))

    await waitFor(() => expect(statusBodies).toEqual([{ active: false }]))
    expect(
      await screen.findByRole('button', { name: /activate/i }),
    ).toBeInTheDocument()
    confirmSpy.mockRestore()
  })

  it('does not flip status in the UI when the status request fails', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    mockAdminFetch((url, init) => {
      if (url.includes('/admin/categories')) {
        return jsonResponse(200, [category])
      }
      if (url.includes('/status') && init?.method === 'PATCH') {
        return jsonResponse(500, {
          status: 500,
          error: 'Server Error',
          message: 'Temporary failure',
        })
      }
      if (url.includes('/admin/products')) {
        return jsonResponse(200, {
          items: [product],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
          first: true,
          last: true,
        })
      }
      return jsonResponse(404, { status: 404, error: 'Not Found', message: 'missing' })
    })

    renderWithProviders(<AppRouter />, {
      route: '/admin/products',
      session: demoAdminSession,
    })

    await screen.findByText(/demo phone/i)
    await user.click(screen.getByRole('button', { name: /deactivate/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/temporary failure/i)
    expect(screen.getByRole('button', { name: /deactivate/i })).toBeInTheDocument()
  })
})
