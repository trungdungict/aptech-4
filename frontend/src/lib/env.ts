import { z } from 'zod'

/**
 * Dev default talks to a host Spring Boot process. Production / Compose builds
 * default to same-origin `/api/v1` so `localhost` is not baked into the bundle
 * (`import.meta.env.DEV` is compile-time false in `vite build`).
 */
const DEFAULT_API_BASE_URL = import.meta.env.DEV
	? 'http://localhost:8080/api/v1'
	: '/api/v1'

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
})

const candidate = import.meta.env.VITE_API_BASE_URL ?? DEFAULT_API_BASE_URL
const parsed = envSchema.safeParse({ VITE_API_BASE_URL: candidate })

/**
 * Browser-safe config only. Never put secrets in `VITE_*` variables.
 */
export const env = parsed.success
	? parsed.data
	: { VITE_API_BASE_URL: DEFAULT_API_BASE_URL }

export const apiBaseUrl = env.VITE_API_BASE_URL
