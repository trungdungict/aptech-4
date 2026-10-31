import { afterEach, describe, expect, it, vi } from 'vitest'

import { formatBytes, validateProductImageFile } from './product-image-upload'

vi.mock('@/lib/env', () => ({
	productImageMaxSizeBytes: 2 * 1024 * 1024,
	productImageAllowedTypes: ['image/jpeg', 'image/png', 'image/webp'],
}))

describe('validateProductImageFile', () => {
	afterEach(() => {
		vi.restoreAllMocks()
	})

	it('accepts a small png', () => {
		const file = new File([new Uint8Array([1, 2, 3])], 'a.png', { type: 'image/png' })
		expect(validateProductImageFile(file)).toEqual({ ok: true })
	})

	it('accepts jpeg and webp below the configured limit', () => {
		expect(
			validateProductImageFile(
				new File([new Uint8Array([1, 2])], 'a.jpg', { type: 'image/jpeg' }),
			),
		).toEqual({ ok: true })
		expect(
			validateProductImageFile(
				new File([new Uint8Array([1, 2])], 'a.webp', { type: 'image/webp' }),
			),
		).toEqual({ ok: true })
	})

	it('rejects exactly 2 MiB', () => {
		const file = new File([new Uint8Array(2 * 1024 * 1024)], 'big.png', { type: 'image/png' })
		const result = validateProductImageFile(file)
		expect(result.ok).toBe(false)
		if (!result.ok) {
			expect(result.message).toMatch(/smaller than/i)
		}
	})

	it('rejects larger than 2 MiB', () => {
		const file = new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'huge.png', {
			type: 'image/png',
		})
		expect(validateProductImageFile(file).ok).toBe(false)
	})

	it('rejects unsupported mime', () => {
		const file = new File([new Uint8Array([1])], 'x.gif', { type: 'image/gif' })
		const result = validateProductImageFile(file)
		expect(result.ok).toBe(false)
		if (!result.ok) {
			expect(result.message).toMatch(/Unsupported/i)
		}
	})

	it('rejects empty file', () => {
		const file = new File([], 'empty.png', { type: 'image/png' })
		expect(validateProductImageFile(file).ok).toBe(false)
	})
})

describe('formatBytes', () => {
	it('formats megabytes', () => {
		expect(formatBytes(2 * 1024 * 1024)).toMatch(/MB/)
	})
})
