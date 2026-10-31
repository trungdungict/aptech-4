import { useEffect, useId, useRef, useState } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiClientError } from '@/lib/api/api-error'
import { productImageMaxSizeBytes } from '@/lib/env'

import { uploadAdminProductImage } from '../api/admin-products-api'
import {
  formatBytes,
  productImageAcceptAttribute,
  validateProductImageFile,
} from '../lib/product-image-upload'

type ProductImageUploadFieldProps = {
  imageUrl: string | null
  onImageUrlChange: (url: string | null) => void
  disabled?: boolean
}

/**
 * Admin product image picker: client validate → multipart upload → set public path.
 * Does not persist Base64; only the returned relative path is written to the form.
 */
export function ProductImageUploadField({
  imageUrl,
  onImageUrlChange,
  disabled = false,
}: ProductImageUploadFieldProps) {
  const inputId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [selectedName, setSelectedName] = useState<string | null>(null)
  const [selectedSize, setSelectedSize] = useState<number | null>(null)
  const [localError, setLocalError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [uploadOk, setUploadOk] = useState<string | null>(null)

  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl)
      }
    }
  }, [previewUrl])

  function clearPreview() {
    setPreviewUrl((prev) => {
      if (prev) {
        URL.revokeObjectURL(prev)
      }
      return null
    })
    setSelectedName(null)
    setSelectedSize(null)
  }

  async function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    setLocalError(null)
    setUploadOk(null)

    if (!file) {
      return
    }

    const validation = validateProductImageFile(file)
    if (!validation.ok) {
      clearPreview()
      setLocalError(validation.message)
      return
    }

    clearPreview()
    const objectUrl = URL.createObjectURL(file)
    setPreviewUrl(objectUrl)
    setSelectedName(file.name)
    setSelectedSize(file.size)

    setUploading(true)
    try {
      const result = await uploadAdminProductImage(file)
      onImageUrlChange(result.imageUrl)
      setUploadOk('Image uploaded. Save the product to persist this path.')
    } catch (error) {
      // Keep the previously assigned path; never invent a fake URL on failure.
      if (error instanceof ApiClientError) {
        setLocalError(error.userMessage)
      } else {
        setLocalError('Upload failed. Try again.')
      }
      clearPreview()
    } finally {
      setUploading(false)
    }
  }

  function handleClear() {
    if (uploading) {
      return
    }
    clearPreview()
    setLocalError(null)
    setUploadOk(null)
    onImageUrlChange(null)
  }

  const displaySrc = previewUrl ?? imageUrl
  const busy = disabled || uploading

  return (
    <div className="space-y-3 rounded-lg border border-border bg-muted/20 p-3">
      <div className="space-y-1">
        <Label htmlFor={inputId}>Product image</Label>
        <p className="text-xs text-muted-foreground">
          JPEG, PNG, or WebP — must be smaller than {formatBytes(productImageMaxSizeBytes)}. Uploaded
          files are stored on the server; only the public path is saved with the product.
        </p>
      </div>

      {displaySrc ? (
        <div className="flex items-start gap-3">
          <img
            src={displaySrc}
            alt=""
            className="size-24 rounded-md border border-border object-contain bg-background"
          />
          <div className="min-w-0 space-y-1 text-xs text-muted-foreground">
            {selectedName ? (
              <p className="truncate text-foreground">
                {selectedName}
                {selectedSize != null ? ` · ${formatBytes(selectedSize)}` : ''}
              </p>
            ) : null}
            {imageUrl ? (
              <p className="break-all font-mono text-[11px] text-foreground/80">{imageUrl}</p>
            ) : null}
          </div>
        </div>
      ) : null}

      <div className="flex flex-wrap items-center gap-2">
        <Input
          ref={inputRef}
          id={inputId}
          type="file"
          accept={productImageAcceptAttribute()}
          disabled={busy}
          className="max-w-xs cursor-pointer text-sm file:mr-2"
          onChange={(event) => {
            void handleFileChange(event)
          }}
        />
        {imageUrl || previewUrl ? (
          <Button type="button" variant="outline" size="sm" disabled={busy} onClick={handleClear}>
            Clear image
          </Button>
        ) : null}
      </div>

      {uploading ? (
        <p className="text-sm text-muted-foreground" role="status">
          Uploading…
        </p>
      ) : null}
      {localError ? (
        <p className="text-sm text-destructive" role="alert">
          {localError}
        </p>
      ) : null}
      {uploadOk ? (
        <p className="text-sm text-emerald-700 dark:text-emerald-400" role="status">
          {uploadOk}
        </p>
      ) : null}
    </div>
  )
}
