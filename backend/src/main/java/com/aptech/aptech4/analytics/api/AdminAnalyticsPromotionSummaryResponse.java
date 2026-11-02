package com.aptech.aptech4.analytics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminAnalyticsPromotionSummaryResponse")
public record AdminAnalyticsPromotionSummaryResponse(
		Instant from,
		Instant to,
		String timezone,
		String currency,
		List<String> revenueStatuses,
		BigDecimal discountTotal,
		long ordersWithPromotionCount,
		long revenueOrderCount,
		List<AdminAnalyticsPromotionBreakdownItemResponse> breakdown,
		boolean empty
) {
}
