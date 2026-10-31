package com.aptech.aptech4.promotion.cache;

/** Spring Cache regions for public promotion placements (ADR-020 R7). */
public final class PromotionCacheNames {

	public static final String HOMEPAGE = "promotionsHomepage";
	public static final String PLACEMENTS = "promotionsPlacements";

	private PromotionCacheNames() {
	}
}
