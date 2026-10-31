import { useEffect, useId, useRef, useState } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { resolveProductImage } from '@/features/catalog/lib/resolve-product-image'
import { ApiClientError } from '@/lib/api/api-error'
import { productImageMaxSizeBytes } from '@/lib/env'
import { cn } from '@/lib/utils'

import { uploadAdminProductImage } from '../api/admin-products-api'
import {
  formatBytes,
  productImageAcceptAttribute,
  validateProductImageFile,
} from '../lib/product-image-upload'

type ProductImageUploadFieldProps = {
  /** Persisted API path (`/uploads/products/…`) or null — written on product save. */
  imageUrl: string | null
  /** Used only for display fallback via {@link resolveProductImage} (not persisted). */
  productSku?: string | null
  productName?: string | null
  onImageUrlChange: (url: string | null) => void
  disabled?: boolean
}

/**
 * Admin product image picker: client validate → multipart upload → set public path.
 * Does not persist Base64; only the returned relative path is written to the form.
 * Catalog/FoodMart fallbacks are display-only when `imageUrl` is empty.
 */
export function ProductImageUploadField({
  imageUrl,
  productSku = null,
  productName = null,
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
  const [currentBroken, setCurrentBroken] = useState(false)
  const [previewBroken, setPreviewBroken] = useState(false)

  const currentSrc = resolveProductImage({
    imageUrl,
    sku: productSku,
    name: productName,
  })

  useEffect(() => {
    setCurrentBroken(false)
  }, [currentSrc])

  useEffect(() => {
    setPreviewBroken(false)
  }, [previewUrl])

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
    setPreviewBroken(false)
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
      // Drop blob preview; current image block now uses the uploaded path.
      clearPreview()
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

  const busy = disabled || uploading
  const showCurrentImage = Boolean(currentSrc) && !currentBroken
  const showNewPreview = Boolean(previewUrl) && !previewBroken

  return (
    <div className="space-y-3 rounded-lg border border-border bg-muted/20 p-3">
      <div className="space-y-1">
        <Label htmlFor={inputId}>Product image</Label>
        <p className="text-xs text-muted-foreground">
          JPEG, PNG, or WebP — must be smaller than {formatBytes(productImageMaxSizeBytes)}. Uploaded
          files are stored on the server; only the public path is saved with the product. Leaving the
          file input empty keeps the current image on save.
        </p>
      </div>

      <div className="space-y-2">
        <p className="text-sm font-medium text-foreground">Current product image</p>
        {showCurrentImage && currentSrc ? (
          <div className="flex items-start gap-3">
            <img
              src={currentSrc}
              alt=""
              className="h-28 w-28 rounded-md border border-border bg-background object-contain"
              onError={() => setCurrentBroken(true)}
            />
            <div className="min-w-0 space-y-1 text-xs text-muted-foreground">
              {imageUrl ? (
                <p className="break-all font-mono text-[11px] text-foreground/80">{imageUrl}</p>
              ) : (
                <p>Catalog display image (not stored as an upload path until you replace it).</p>
              )}
            </div>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground" role="status">
            No product image
          </p>
        )}
      </div>

      {showNewPreview && previewUrl ? (
        <div className="space-y-2">
          <p className="text-sm font-medium text-foreground">New image preview</p>
          <div className="flex items-start gap-3">
            <img
              src={previewUrl}
              alt=""
              className={cn(
                'h-28 w-28 rounded-md border border-border bg-background object-contain',
              )}
              onError={() => setPreviewBroken(true)}
            />
            <div className="min-w-0 space-y-1 text-xs text-muted-foreground">
              {selectedName ? (
                <p className="truncate text-foreground">
                  {selectedName}
                  {selectedSize != null ? ` · ${formatBytes(selectedSize)}` : ''}
                </p>
              ) : null}
              <p>Selected file — upload in progress</p>
            </div>
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
