package com.aptech.aptech4.analytics.api;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminAnalyticsPromotionBreakdownItemResponse")
public record AdminAnalyticsPromotionBreakdownItemResponse(
		String campaignSlug,
		String strategyCode,
		String campaignName,
		String strategyName,
		String strategyType,
		BigDecimal discountTotal,
		long orderCount
) {
}
