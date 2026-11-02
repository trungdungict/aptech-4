package com.aptech.aptech4.analytics.api;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminAnalyticsTopProductsResponse")
public record AdminAnalyticsTopProductsResponse(
		Instant from,
		Instant to,
		String timezone,
		List<String> revenueStatuses,
		List<AdminAnalyticsTopProductItemResponse> items,
		boolean empty,
		String note
) {
}
