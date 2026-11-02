package com.aptech.aptech4.analytics;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;

import com.aptech.aptech4.common.api.ApiException;

/**
 * Inclusive-start / exclusive-end Instant window used by analytics repository queries
 * ({@code placed_at >= from AND placed_at < to}).
 */
public record AnalyticsDateRange(Instant from, Instant to) {

	/**
	 * Default analytics window: last {@link AnalyticsMetrics#DEFAULT_WINDOW_DAYS} days ending at
	 * {@code to} (or now). Rejects inverted ranges and spans over
	 * {@link AnalyticsMetrics#MAX_RANGE_DAYS} days.
	 */
	public static AnalyticsDateRange normalize(Instant from, Instant to) {
		Instant end = to == null ? Instant.now() : to;
		Instant start = from == null ? end.minus(AnalyticsMetrics.DEFAULT_WINDOW_DAYS, ChronoUnit.DAYS) : from;
		if (!start.isBefore(end)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "from must be before to");
		}
		if (ChronoUnit.DAYS.between(start, end) > AnalyticsMetrics.MAX_RANGE_DAYS) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"Bad Request",
					"Date range must be at most " + AnalyticsMetrics.MAX_RANGE_DAYS + " days"
			);
		}
		return new AnalyticsDateRange(start, end);
	}

	/**
	 * Full-history window for order list/price filters (bounded only by from &lt; to).
	 * Defaults: EPOCH → now+1d.
	 */
	public static AnalyticsDateRange normalizeList(Instant from, Instant to) {
		Instant end = to == null ? Instant.now().plus(1, ChronoUnit.DAYS) : to;
		Instant start = from == null ? Instant.EPOCH : from;
		if (!start.isBefore(end)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", "from must be before to");
		}
		return new AnalyticsDateRange(start, end);
	}
}
