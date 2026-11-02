package com.aptech.aptech4.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.aptech.aptech4.catalog.application.AdminInventoryService;
import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.persistence.ShopOrderRepository;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

	@Mock
	ShopOrderRepository shopOrderRepository;

	@Mock
	InventoryRepository inventoryRepository;

	@Mock
	AdminInventoryService adminInventoryService;

	AdminAnalyticsService service;

	@BeforeEach
	void setUp() {
		service = new AdminAnalyticsService(
				shopOrderRepository, inventoryRepository, adminInventoryService, 5, 20
		);
	}

	@Test
	void salesSummaryUsesRevenueStatusesAndZeroWhenEmpty() {
		when(shopOrderRepository.sumTotalsByStatuses(eq(AnalyticsMetrics.REVENUE_STATUSES), any(), any()))
				.thenReturn(new BigDecimal("150.00"));
		when(shopOrderRepository.countByStatuses(eq(AnalyticsMetrics.REVENUE_STATUSES), any(), any()))
				.thenReturn(3L);

		Map<String, Object> summary = service.salesSummary(
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-02-01T00:00:00Z")
		);

		assertThat(summary.get("orderCount")).isEqualTo(3L);
		assertThat(summary.get("revenueTotal")).isEqualTo(new BigDecimal("150.00"));
		assertThat(summary.get("currency")).isEqualTo("VND");
		assertThat(summary.get("empty")).isEqualTo(false);
		assertThat(summary.get("timezone")).isEqualTo(AnalyticsMetrics.TIMEZONE);
		assertThat(summary.get("revenueStatuses")).asList().contains(OrderStatus.PAID.name());
		assertThat(summary.get("revenueDefinition").toString()).contains("Excludes");
		assertThat(summary).doesNotContainKey("averageOrderValue");
	}

	@Test
	void salesSummaryEmptyIsDistinctFromFailure() {
		when(shopOrderRepository.sumTotalsByStatuses(eq(AnalyticsMetrics.REVENUE_STATUSES), any(), any()))
				.thenReturn(BigDecimal.ZERO);
		when(shopOrderRepository.countByStatuses(eq(AnalyticsMetrics.REVENUE_STATUSES), any(), any()))
				.thenReturn(0L);

		Map<String, Object> summary = service.salesSummary(
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-02-01T00:00:00Z")
		);

		assertThat(summary.get("orderCount")).isEqualTo(0L);
		assertThat(summary.get("revenueTotal")).isEqualTo(BigDecimal.ZERO);
		assertThat(summary.get("empty")).isEqualTo(true);
	}

	@Test
	void lowStockUsesConfiguredThreshold() {
		when(inventoryRepository.findActiveLowStock(eq(5), any(Pageable.class))).thenReturn(List.of());

		Map<String, Object> body = service.lowStockProducts(null, 10);

		assertThat(body.get("threshold")).isEqualTo(5);
		assertThat(body.get("availableDefinition").toString()).contains("quantity_on_hand - quantity_reserved");
	}

	@Test
	void attentionCountsSumsPaidAndProcessing() {
		when(shopOrderRepository.countByStatuses(eq(List.of(OrderStatus.PAID)), any(), any())).thenReturn(23L);
		when(shopOrderRepository.countByStatuses(eq(List.of(OrderStatus.PROCESSING)), any(), any())).thenReturn(30L);

		Map<String, Object> body = service.attentionCounts();

		assertThat(body.get("paid")).isEqualTo(23L);
		assertThat(body.get("processing")).isEqualTo(30L);
		assertThat(body.get("totalMatching")).isEqualTo(53L);
		assertThat(body.get("attentionStatuses")).asList().contains("PAID", "PROCESSING");
	}

	@Test
	void salesSummaryRejectsInvertedRange() {
		assertThatThrownBy(() -> service.salesSummary(
				Instant.parse("2026-02-01T00:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z")
		)).isInstanceOf(ApiException.class)
				.hasMessageContaining("from must be before to");
	}

	@Test
	void overviewIncludesAovAndInventoryEmbed() {
		when(shopOrderRepository.countGroupedByStatus(any(), any())).thenReturn(List.of(
				new Object[] {OrderStatus.PAID, 2L},
				new Object[] {OrderStatus.CANCELLED, 1L}
		));
		when(shopOrderRepository.countByStatuses(eq(AnalyticsMetrics.REVENUE_STATUSES), any(), any()))
				.thenReturn(2L);
		when(shopOrderRepository.sumTotalsByStatuses(eq(AnalyticsMetrics.REVENUE_STATUSES), any(), any()))
				.thenReturn(new BigDecimal("100.00"));
		when(shopOrderRepository.countByStatuses(eq(List.of(OrderStatus.PAID)), any(), any())).thenReturn(4L);
		when(shopOrderRepository.countByStatuses(eq(List.of(OrderStatus.PROCESSING)), any(), any())).thenReturn(1L);
		when(adminInventoryService.summary()).thenReturn(
				new com.aptech.aptech4.catalog.api.AdminInventorySummaryResponse(10, 7, 2, 1, 5, "def")
		);

		var overview = service.overview(
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-02-01T00:00:00Z")
		);

		assertThat(overview.placedOrderCount()).isEqualTo(3L);
		assertThat(overview.revenueOrderCount()).isEqualTo(2L);
		assertThat(overview.revenueTotal()).isEqualByComparingTo("100.00");
		assertThat(overview.averageOrderValue()).isEqualByComparingTo("50.00");
		assertThat(overview.cancellationCount()).isEqualTo(1L);
		assertThat(overview.cancellationRate()).isEqualTo(1.0 / 3.0);
		assertThat(overview.statusCounts()).containsEntry("PENDING_PAYMENT", 0L);
		assertThat(overview.statusCounts()).containsEntry("PAID", 2L);
		assertThat(overview.attention().totalMatching()).isEqualTo(5L);
		assertThat(overview.inventory().lowStockThreshold()).isEqualTo(5);
		assertThat(overview.empty()).isFalse();
	}

	@Test
	void topProductsRejectsLimitAboveMax() {
		assertThatThrownBy(() -> service.topProducts(
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-02-01T00:00:00Z"),
				21
		)).isInstanceOf(ApiException.class)
				.hasMessageContaining("limit must be between");
	}

	@Test
	void utcDaysIntersectingZeroFillsHalfOpenWindow() {
		assertThat(AdminAnalyticsService.utcDaysIntersecting(
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-03T00:00:00Z")
		)).containsExactly(
				java.time.LocalDate.parse("2026-01-01"),
				java.time.LocalDate.parse("2026-01-02")
		);
		assertThat(AdminAnalyticsService.utcDaysIntersecting(
				Instant.parse("2026-01-01T12:00:00Z"),
				Instant.parse("2026-01-03T12:00:00Z")
		)).containsExactly(
				java.time.LocalDate.parse("2026-01-01"),
				java.time.LocalDate.parse("2026-01-02"),
				java.time.LocalDate.parse("2026-01-03")
		);
	}
}
