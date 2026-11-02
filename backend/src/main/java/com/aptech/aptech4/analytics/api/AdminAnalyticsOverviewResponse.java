package com.aptech.aptech4.analytics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.aptech.aptech4.catalog.api.AdminInventorySummaryResponse;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminAnalyticsOverviewResponse")
public record AdminAnalyticsOverviewResponse(
		Instant from,
		Instant to,
		String timezone,
		String currency,
		List<String> revenueStatuses,
		long placedOrderCount,
		long revenueOrderCount,
		BigDecimal revenueTotal,
		BigDecimal averageOrderValue,
		long cancellationCount,
		double cancellationRate,
		Map<String, Long> statusCounts,
		AdminAnalyticsAttentionResponse attention,
		AdminInventorySummaryResponse inventory,
		boolean empty,
		String revenueDefinition
) {
}
