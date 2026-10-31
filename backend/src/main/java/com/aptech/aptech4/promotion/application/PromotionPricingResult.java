package com.aptech.aptech4.promotion.application;

import java.math.BigDecimal;
import java.util.List;

/**
 * Order-level promotion pricing outcome (ADR-020 D2).
 */
public record PromotionPricingResult(
		BigDecimal subtotalAmount,
		BigDecimal discountAmount,
		String promotionSummary,
		List<AppliedPromotion> appliedPromotions
) {
	public static PromotionPricingResult none(BigDecimal subtotalAmount) {
		BigDecimal sub = subtotalAmount == null ? BigDecimal.ZERO : subtotalAmount;
		return new PromotionPricingResult(sub, BigDecimal.ZERO.setScale(2), null, List.of());
	}
}
