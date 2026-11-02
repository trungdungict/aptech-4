package com.aptech.aptech4.analytics.api;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminAnalyticsCategorySalesResponse", description = """
		Sales by live products.category_id. Historical category attribution is not preserved.
		""")
public record AdminAnalyticsCategorySalesResponse(
		Instant from,
		Instant to,
		String timezone,
		List<String> revenueStatuses,
		List<AdminAnalyticsCategorySalesItemResponse> items,
		boolean empty,
		String attributionNote
) {
}
