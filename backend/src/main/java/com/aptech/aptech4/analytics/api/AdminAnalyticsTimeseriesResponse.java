package com.aptech.aptech4.analytics.api;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminAnalyticsTimeseriesResponse")
public record AdminAnalyticsTimeseriesResponse(
		Instant from,
		Instant to,
		String timezone,
		String bucket,
		List<AdminAnalyticsTimeseriesPointResponse> points,
		boolean empty
) {
}
