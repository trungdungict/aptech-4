import { productImageAllowedTypes, productImageMaxSizeBytes } from '@/lib/env'

export type ProductImageClientValidation =
	| { ok: true }
	| { ok: false; message: string }

/**
 * Client-side UX checks only. Backend PRODUCT_IMAGE_* is authoritative.
 * Rejects files with size >= configured max (exactly 2 MiB rejected by default).
 */
export function validateProductImageFile(file: File): ProductImageClientValidation {
	if (!file || file.size <= 0) {
		return { ok: false, message: 'Choose a non-empty image file.' }
	}
	if (file.size >= productImageMaxSizeBytes) {
		return {
			ok: false,
			message: `Image must be smaller than ${formatBytes(productImageMaxSizeBytes)}.`,
		}
	}
	const type = (file.type || '').toLowerCase()
	if (!productImageAllowedTypes.includes(type)) {
		return {
			ok: false,
			message: `Unsupported format. Allowed: ${productImageAllowedTypes.join(', ')}.`,
		}
	}
	return { ok: true }
}

export function formatBytes(bytes: number): string {
	if (bytes < 1024) {
		return `${bytes} B`
	}
	if (bytes < 1024 * 1024) {
		return `${(bytes / 1024).toFixed(0)} KB`
	}
	return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

export function productImageAcceptAttribute(): string {
	return productImageAllowedTypes.join(',')
}
