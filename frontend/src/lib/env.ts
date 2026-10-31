import { z } from 'zod'

/**
 * Dev default talks to a host Spring Boot process. Production / Compose builds
 * default to same-origin `/api/v1` so `localhost` is not baked into the bundle
 * (`import.meta.env.DEV` is compile-time false in `vite build`).
 */
const DEFAULT_API_BASE_URL = import.meta.env.DEV
	? 'http://localhost:8080/api/v1'
	: '/api/v1'

/** Must stay aligned with backend PRODUCT_IMAGE_MAX_SIZE_BYTES (backend is authoritative). */
const DEFAULT_PRODUCT_IMAGE_MAX_SIZE_BYTES = 2 * 1024 * 1024
const DEFAULT_PRODUCT_IMAGE_ALLOWED_TYPES = 'image/jpeg,image/png,image/webp'

/**
 * Accept absolute URLs (dev / cross-origin) or a path-absolute prefix (Compose/EC2
 * same-origin via nginx `/api/v1`). Vite bakes this at build time — runtime OS env
 * cannot change the value already compiled into the bundle.
 */
function isApiBaseUrl(value: string): boolean {
	if (value.startsWith('/') && !value.startsWith('//')) {
		return value.length > 1
	}
	try {
		const parsed = new URL(value)
		return parsed.protocol === 'http:' || parsed.protocol === 'https:'
	} catch {
		return false
	}
}

const envSchema = z.object({
	VITE_API_BASE_URL: z.string().refine(isApiBaseUrl, {
		message: 'Must be an absolute http(s) URL or a path starting with / (e.g. /api/v1)',
	}),
	VITE_PRODUCT_IMAGE_MAX_SIZE_BYTES: z.coerce.number().int().positive(),
	VITE_PRODUCT_IMAGE_ALLOWED_TYPES: z.string().min(1),
})

const candidate = {
	VITE_API_BASE_URL: import.meta.env.VITE_API_BASE_URL ?? DEFAULT_API_BASE_URL,
	VITE_PRODUCT_IMAGE_MAX_SIZE_BYTES:
		import.meta.env.VITE_PRODUCT_IMAGE_MAX_SIZE_BYTES ?? DEFAULT_PRODUCT_IMAGE_MAX_SIZE_BYTES,
	VITE_PRODUCT_IMAGE_ALLOWED_TYPES:
		import.meta.env.VITE_PRODUCT_IMAGE_ALLOWED_TYPES ?? DEFAULT_PRODUCT_IMAGE_ALLOWED_TYPES,
}

const parsed = envSchema.safeParse(candidate)

/**
 * Browser-safe config only. Never put secrets in `VITE_*` variables.
 * Upload size/type mirrors are UX only — backend PRODUCT_IMAGE_* remains authoritative.
 */
export const env = parsed.success
	? parsed.data
	: {
			VITE_API_BASE_URL: DEFAULT_API_BASE_URL,
			VITE_PRODUCT_IMAGE_MAX_SIZE_BYTES: DEFAULT_PRODUCT_IMAGE_MAX_SIZE_BYTES,
			VITE_PRODUCT_IMAGE_ALLOWED_TYPES: DEFAULT_PRODUCT_IMAGE_ALLOWED_TYPES,
		}

export const apiBaseUrl = env.VITE_API_BASE_URL

export const productImageMaxSizeBytes = env.VITE_PRODUCT_IMAGE_MAX_SIZE_BYTES

export const productImageAllowedTypes = env.VITE_PRODUCT_IMAGE_ALLOWED_TYPES
	.split(',')
	.map((value) => value.trim().toLowerCase())
	.filter(Boolean)
