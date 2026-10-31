import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiClientError } from '@/lib/api/api-error'

import { ProductImageUploadField } from './ProductImageUploadField'

const uploadAdminProductImage = vi.fn()

vi.mock('@/lib/env', () => ({
  productImageMaxSizeBytes: 2 * 1024 * 1024,
  productImageAllowedTypes: ['image/jpeg', 'image/png', 'image/webp'],
}))

vi.mock('../api/admin-products-api', () => ({
  uploadAdminProductImage: (...args: unknown[]) => uploadAdminProductImage(...args),
}))

describe('ProductImageUploadField', () => {
  const revokeSpy = vi.fn()
  const createSpy = vi.fn(() => 'blob:mock-preview')

  beforeEach(() => {
    uploadAdminProductImage.mockReset()
    revokeSpy.mockReset()
    createSpy.mockClear()
    vi.stubGlobal('URL', {
      createObjectURL: createSpy,
      revokeObjectURL: revokeSpy,
    })
  })

  afterEach(() => {
    cleanup()
    vi.unstubAllGlobals()
  })

  it('shows preview for a valid selection and assigns returned imageUrl', async () => {
    const user = userEvent.setup()
    const onImageUrlChange = vi.fn()
    uploadAdminProductImage.mockResolvedValue({
      imageUrl: '/uploads/products/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png',
      contentType: 'image/png',
      sizeBytes: 4,
    })

    render(<ProductImageUploadField imageUrl={null} onImageUrlChange={onImageUrlChange} />)

    const file = new File([new Uint8Array([1, 2, 3, 4])], 'apple.png', { type: 'image/png' })
    await user.upload(screen.getByLabelText(/product image/i), file)

    expect(createSpy).toHaveBeenCalled()
    expect(await screen.findByText(/image uploaded/i)).toBeInTheDocument()
    expect(onImageUrlChange).toHaveBeenCalledWith(
      '/uploads/products/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png',
    )
    expect(uploadAdminProductImage).toHaveBeenCalledTimes(1)
    const sent = uploadAdminProductImage.mock.calls[0][0] as File
    expect(sent).toBeInstanceOf(File)
    expect(sent.name).toBe('apple.png')
  })

  it('rejects unsupported type before upload', async () => {
    const onImageUrlChange = vi.fn()
    render(<ProductImageUploadField imageUrl={null} onImageUrlChange={onImageUrlChange} />)

    const file = new File([new Uint8Array([1])], 'x.gif', { type: 'image/gif' })
    // fireEvent bypasses HTML accept filtering so client-side validation is exercised.
    fireEvent.change(screen.getByLabelText(/product image/i), { target: { files: [file] } })

    expect(await screen.findByRole('alert')).toHaveTextContent(/unsupported/i)
    expect(uploadAdminProductImage).not.toHaveBeenCalled()
    expect(onImageUrlChange).not.toHaveBeenCalled()
  })

  it('rejects exact 2 MiB before upload', async () => {
    const user = userEvent.setup()
    render(<ProductImageUploadField imageUrl={null} onImageUrlChange={vi.fn()} />)

    const file = new File([new Uint8Array(2 * 1024 * 1024)], 'big.png', { type: 'image/png' })
    await user.upload(screen.getByLabelText(/product image/i), file)

    expect(await screen.findByRole('alert')).toHaveTextContent(/smaller than/i)
    expect(uploadAdminProductImage).not.toHaveBeenCalled()
  })

  it('keeps previous imageUrl when upload fails', async () => {
    const user = userEvent.setup()
    const previous = '/uploads/products/11111111-1111-4111-8111-111111111111.png'
    const onImageUrlChange = vi.fn()
    uploadAdminProductImage.mockRejectedValue(
      new ApiClientError({ status: 400, error: 'Bad Request', message: 'Invalid or corrupted image' }),
    )

    render(<ProductImageUploadField imageUrl={previous} onImageUrlChange={onImageUrlChange} />)

    const file = new File([new Uint8Array([1, 2, 3])], 'bad.png', { type: 'image/png' })
    await user.upload(screen.getByLabelText(/product image/i), file)

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid or corrupted/i)
    expect(onImageUrlChange).not.toHaveBeenCalled()
    expect(screen.getByText(previous)).toBeInTheDocument()
  })

  it('disables picker while uploading to prevent duplicate submission', async () => {
    const user = userEvent.setup()
    let resolveUpload: (value: unknown) => void = () => {}
    uploadAdminProductImage.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveUpload = resolve
        }),
    )

    render(<ProductImageUploadField imageUrl={null} onImageUrlChange={vi.fn()} />)
    const input = screen.getByLabelText(/product image/i) as HTMLInputElement
    const file = new File([new Uint8Array([9, 9])], 'slow.png', { type: 'image/png' })
    await user.upload(input, file)

    expect(await screen.findByText(/uploading/i)).toBeInTheDocument()
    expect(input).toBeDisabled()

    resolveUpload({
      imageUrl: '/uploads/products/22222222-2222-4222-8222-222222222222.png',
      contentType: 'image/png',
      sizeBytes: 2,
    })
    await waitFor(() => expect(input).not.toBeDisabled())
  })

  it('revokes preview object URL on unmount', async () => {
    const user = userEvent.setup()
    uploadAdminProductImage.mockResolvedValue({
      imageUrl: '/uploads/products/33333333-3333-4333-8333-333333333333.png',
      contentType: 'image/png',
      sizeBytes: 2,
    })

    const { unmount } = render(
      <ProductImageUploadField imageUrl={null} onImageUrlChange={vi.fn()} />,
    )
    await user.upload(
      screen.getByLabelText(/product image/i),
      new File([new Uint8Array([1, 2])], 'x.png', { type: 'image/png' }),
    )
    await screen.findByText(/image uploaded/i)

    unmount()
    expect(revokeSpy).toHaveBeenCalledWith('blob:mock-preview')
  })
})
