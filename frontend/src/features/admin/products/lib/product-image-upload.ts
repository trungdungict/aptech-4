import { productImageAllowedTypes, productImageMaxSizeBytes } from '@/lib/env'
import { i18n } from '@/lib/i18n'

export type ProductImageClientValidation =
	| { ok: true }
	| { ok: false; message: string }

/**
 * Client-side UX checks only. Backend PRODUCT_IMAGE_* is authoritative.
 * Rejects files with size >= configured max (exactly 2 MiB rejected by default).
 */
export function validateProductImageFile(file: File): ProductImageClientValidation {
	if (!file || file.size <= 0) {
		return { ok: false, message: i18n.t('products.image.errors.empty', { ns: 'admin' }) }
	}
	if (file.size >= productImageMaxSizeBytes) {
		return {
			ok: false,
			message: i18n.t('products.image.errors.tooLarge', {
				ns: 'admin',
				max: formatBytes(productImageMaxSizeBytes),
			}),
		}
	}
	const type = (file.type || '').toLowerCase()
	if (!productImageAllowedTypes.includes(type)) {
		return {
			ok: false,
			message: i18n.t('products.image.errors.unsupported', {
				ns: 'admin',
				types: productImageAllowedTypes.join(', '),
			}),
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
