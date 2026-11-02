package com.aptech.aptech4.analytics.api;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminAnalyticsTimeseriesPointResponse")
public record AdminAnalyticsTimeseriesPointResponse(
		@Schema(description = "UTC calendar day yyyy-MM-dd") String date,
		long placedOrderCount,
		BigDecimal revenueTotal
) {
}
