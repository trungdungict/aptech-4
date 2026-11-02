package com.aptech.aptech4.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

import com.aptech.aptech4.common.api.ApiException;

class AnalyticsDateRangeTest {

	@Test
	void normalizeDefaultsToThirtyDayWindowEndingAtTo() {
		Instant to = Instant.parse("2026-03-31T12:00:00Z");
		AnalyticsDateRange range = AnalyticsDateRange.normalize(null, to);
		assertThat(range.to()).isEqualTo(to);
		assertThat(range.from()).isEqualTo(to.minus(30, ChronoUnit.DAYS));
	}

	@Test
	void normalizeRejectsEqualBounds() {
		Instant t = Instant.parse("2026-01-01T00:00:00Z");
		assertThatThrownBy(() -> AnalyticsDateRange.normalize(t, t))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("from must be before to");
	}

	@Test
	void normalizeRejectsMoreThanMaxDays() {
		Instant from = Instant.parse("2025-01-01T00:00:00Z");
		Instant to = from.plus(400, ChronoUnit.DAYS);
		assertThatThrownBy(() -> AnalyticsDateRange.normalize(from, to))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("366");
	}

	@Test
	void normalizeListDefaultsToEpochThroughNearFuture() {
		AnalyticsDateRange range = AnalyticsDateRange.normalizeList(null, null);
		assertThat(range.from()).isEqualTo(Instant.EPOCH);
		assertThat(range.to()).isAfter(Instant.now());
	}

	@Test
	void normalizeListAllowsLongHistory() {
		Instant from = Instant.parse("2020-01-01T00:00:00Z");
		Instant to = Instant.parse("2026-01-01T00:00:00Z");
		AnalyticsDateRange range = AnalyticsDateRange.normalizeList(from, to);
		assertThat(range.from()).isEqualTo(from);
		assertThat(range.to()).isEqualTo(to);
	}
}
