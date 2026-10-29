package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.aptech.aptech4.catalog.persistence.InventoryRepository;
import com.aptech.aptech4.chat.config.AiProperties;
import com.aptech.aptech4.order.domain.OrderStatus;
import com.aptech.aptech4.order.persistence.ShopOrderRepository;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

	@Mock
	ShopOrderRepository shopOrderRepository;

	@Mock
	InventoryRepository inventoryRepository;

	AiProperties aiProperties = new AiProperties();

	AdminAnalyticsService service;

	@BeforeEach
	void setUp() {
		aiProperties.setLowStockThreshold(5);
		aiProperties.setMaxToolResults(20);
		service = new AdminAnalyticsService(shopOrderRepository, inventoryRepository, aiProperties);
	}

	@Test
	void salesSummaryUsesRevenueStatusesAndZeroWhenEmpty() {
		when(shopOrderRepository.sumTotalsByStatuses(eq(AdminAnalyticsService.REVENUE_STATUSES), any(), any()))
				.thenReturn(new BigDecimal("150.00"));
		when(shopOrderRepository.countByStatuses(eq(AdminAnalyticsService.REVENUE_STATUSES), any(), any()))
				.thenReturn(3L);

		Map<String, Object> summary = service.salesSummary(
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-02-01T00:00:00Z")
		);

		assertThat(summary.get("orderCount")).isEqualTo(3L);
		assertThat(summary.get("revenueTotal")).isEqualTo(new BigDecimal("150.00"));
		assertThat(summary.get("currency")).isEqualTo("VND");
		assertThat(summary.get("empty")).isEqualTo(false);
		assertThat(summary.get("revenueStatuses")).asList().contains(OrderStatus.PAID.name());
		assertThat(summary.get("revenueDefinition").toString()).contains("Excludes");
	}

	@Test
	void salesSummaryEmptyIsDistinctFromFailure() {
		when(shopOrderRepository.sumTotalsByStatuses(eq(AdminAnalyticsService.REVENUE_STATUSES), any(), any()))
				.thenReturn(BigDecimal.ZERO);
		when(shopOrderRepository.countByStatuses(eq(AdminAnalyticsService.REVENUE_STATUSES), any(), any()))
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
}
