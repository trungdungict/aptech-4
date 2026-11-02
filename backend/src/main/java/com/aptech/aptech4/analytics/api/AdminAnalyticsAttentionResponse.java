package com.aptech.aptech4.analytics.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminAnalyticsAttentionResponse")
public record AdminAnalyticsAttentionResponse(
		@Schema(description = "PAID orders needing fulfillment (all dates)") long paid,
		@Schema(description = "PROCESSING orders (all dates)") long processing,
		@Schema(description = "paid + processing") long totalMatching
) {
}
