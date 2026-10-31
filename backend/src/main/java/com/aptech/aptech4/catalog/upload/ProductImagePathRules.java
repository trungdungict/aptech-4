package com.aptech.aptech4.catalog.upload;

/**
 * Product-scoped path validation. Rejects promotion paths (ADR-020 path isolation).
 */
public final class ProductImagePathRules {

	private ProductImagePathRules() {
	}

	/**
	 * @return normalized path or {@code null} when blank (clear image)
	 */
	public static String normalizeOrNull(String raw) {
		return ImageUploadPathRules.normalizeOrNull(UploadPurpose.PRODUCT, raw);
	}
}
