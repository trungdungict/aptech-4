package com.aptech.aptech4.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.aptech.aptech4.order.domain.OrderStatus;

class AnalyticsMetricsTest {

	@Test
	void revenueStatusesMatchFrozenSet() {
		assertThat(AnalyticsMetrics.REVENUE_STATUSES).containsExactly(
				OrderStatus.PAID,
				OrderStatus.PROCESSING,
				OrderStatus.SHIPPED,
				OrderStatus.COMPLETED
		);
		assertThat(AnalyticsMetrics.REVENUE_STATUSES).doesNotContain(
				OrderStatus.PENDING_PAYMENT,
				OrderStatus.CANCELLED,
				OrderStatus.FAILED
		);
	}

	@Test
	void averageOrderValueIsZeroWhenEmpty() {
		assertThat(AnalyticsMetrics.averageOrderValue(new BigDecimal("100.00"), 0))
				.isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(AnalyticsMetrics.averageOrderValue(null, 0))
				.isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void averageOrderValueDividesWithScaleTwo() {
		assertThat(AnalyticsMetrics.averageOrderValue(new BigDecimal("100.00"), 3))
				.isEqualByComparingTo(new BigDecimal("33.33"));
	}
}
