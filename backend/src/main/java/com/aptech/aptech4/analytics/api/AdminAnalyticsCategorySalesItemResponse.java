package com.aptech.aptech4.analytics.api;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminAnalyticsCategorySalesItemResponse")
public record AdminAnalyticsCategorySalesItemResponse(
		UUID categoryId,
		String categoryName,
		long unitsSold,
		BigDecimal revenue,
		long orderLineCount
) {
}
