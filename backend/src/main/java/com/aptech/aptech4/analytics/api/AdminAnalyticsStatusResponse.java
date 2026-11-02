package com.aptech.aptech4.analytics.api;

import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminAnalyticsStatusResponse")
public record AdminAnalyticsStatusResponse(
		Instant from,
		Instant to,
		String timezone,
		Map<String, Long> counts,
		long total,
		boolean empty
) {
}
