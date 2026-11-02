package com.aptech.aptech4.analytics.api;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminAnalyticsTopProductItemResponse")
public record AdminAnalyticsTopProductItemResponse(
		String productSku,
		String productName,
		UUID productId,
		long unitsSold,
		BigDecimal revenue,
		String path
) {
}
