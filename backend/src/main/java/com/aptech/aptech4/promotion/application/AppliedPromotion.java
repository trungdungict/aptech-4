package com.aptech.aptech4.promotion.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One applied pricing campaign — fields needed to build {@code order_promotion_snapshots} in P22-T02.
 */
public record AppliedPromotion(
		UUID campaignId,
		UUID strategyId,
		String campaignName,
		String campaignSlug,
		String strategyName,
		String strategyType,
		String strategyCode,
		int priority,
		boolean stackable,
		BigDecimal discountPercent,
		BigDecimal discountAmountRule,
		BigDecimal maxDiscountAmount,
		BigDecimal eligibleSubtotal,
		BigDecimal discountAmount
) {
}
