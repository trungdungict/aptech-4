import { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  formatBytes,
  productImageAcceptAttribute,
  validateProductImageFile,
} from '@/features/admin/products/lib/product-image-upload'
import { ApiClientError } from '@/lib/api/api-error'
import { productImageMaxSizeBytes } from '@/lib/env'

import { uploadAdminPromotionImage } from '../api/admin-promotions-api'

type PromotionImageUploadFieldProps = {
  label: string
  imageUrl: string | null
  onImageUrlChange: (url: string | null) => void
  disabled?: boolean
}

/**
 * Shared Admin upload with purpose=PROMOTION — stores only the returned path.
 */
export function PromotionImageUploadField({
  label,
  imageUrl,
  onImageUrlChange,
  disabled = false,
}: PromotionImageUploadFieldProps) {
  const { t } = useTranslation('admin')
  const inputId = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
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
    setUploading(true)
    try {
      const result = await uploadAdminPromotionImage(file)
      onImageUrlChange(result.imageUrl)
      setUploadOk(t('promotions.upload.uploaded'))
    } catch (error) {
      setLocalError(error instanceof ApiClientError ? error.userMessage : t('promotions.upload.failed'))
      clearPreview()
    } finally {
      setUploading(false)
    }
  }

  const displaySrc = previewUrl ?? imageUrl
  const busy = disabled || uploading

  return (
    <div className="space-y-3 rounded-lg border border-border bg-muted/20 p-3">
      <div className="space-y-1">
        <Label htmlFor={inputId}>{label}</Label>
        <p className="text-xs text-muted-foreground">
          {t('promotions.upload.hint', { max: formatBytes(productImageMaxSizeBytes) })}
        </p>
      </div>
      {displaySrc ? (
        <div className="overflow-hidden rounded-md border border-border bg-[#f4f7f5]">
          <img
            src={displaySrc}
            alt=""
            className="mx-auto max-h-40 w-full object-contain"
            width={640}
            height={360}
          />
        </div>
      ) : null}
      {imageUrl ? (
        <p className="break-all font-mono text-xs text-muted-foreground">{imageUrl}</p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <Input
          ref={inputRef}
          id={inputId}
          type="file"
          accept={productImageAcceptAttribute()}
          disabled={busy}
          onChange={(event) => void handleFileChange(event)}
        />
        <Button
          type="button"
          variant="outline"
          disabled={busy || !imageUrl}
          onClick={() => {
            clearPreview()
            setLocalError(null)
            setUploadOk(null)
            onImageUrlChange(null)
          }}
        >
          {t('promotions.upload.clear')}
        </Button>
      </div>
      {uploading ? <p className="text-xs text-muted-foreground">{t('promotions.upload.uploading')}</p> : null}
      {uploadOk ? <p className="text-xs text-emerald-800">{uploadOk}</p> : null}
      {localError ? (
        <p className="text-xs text-destructive" role="alert">
          {localError}
        </p>
      ) : null}
    </div>
  )
}
