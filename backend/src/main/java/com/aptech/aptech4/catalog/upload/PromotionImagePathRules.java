package com.aptech.aptech4.catalog.upload;

/**
 * Promotion-scoped path validation. Rejects product paths (ADR-020 path isolation).
 */
public final class PromotionImagePathRules {

	private PromotionImagePathRules() {
	}

	/**
	 * @return normalized path or {@code null} when blank (clear image)
	 */
	public static String normalizeOrNull(String raw) {
		return ImageUploadPathRules.normalizeOrNull(UploadPurpose.PROMOTION, raw);
	}
}
