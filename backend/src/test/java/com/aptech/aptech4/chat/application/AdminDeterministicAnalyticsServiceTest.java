package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aptech.aptech4.chat.api.ChatLanguage;
import com.aptech.aptech4.chat.application.AdminDeterministicAnalyticsService.DeterministicAnswer;
import com.aptech.aptech4.order.domain.OrderStatus;

@ExtendWith(MockitoExtension.class)
class AdminDeterministicAnalyticsServiceTest {

	@Mock
	AdminAnalyticsService adminAnalyticsService;

	@Mock
	AdminInventoryFacade adminInventoryFacade;

	AdminDeterministicAnalyticsService service;

	@BeforeEach
	void setUp() {
		service = new AdminDeterministicAnalyticsService(adminAnalyticsService, adminInventoryFacade);
	}

	@Test
	void attentionAnswerUsesVerifiedCountsWithoutAuthDisclaimer() {
		Map<String, Object> counts = new LinkedHashMap<>();
		counts.put("paid", 23L);
		counts.put("processing", 30L);
		counts.put("totalMatching", 53L);
		when(adminAnalyticsService.attentionCounts()).thenReturn(counts);

		DeterministicAnswer answer = service.answer(
				AdminAnalyticsQueryKind.ORDERS_NEEDING_ATTENTION,
				ChatLanguage.EN
		);

		assertThat(answer).isNotNull();
		assertThat(answer.reply()).isEqualTo("53 orders need processing: 23 PAID and 30 PROCESSING.");
		assertThat(answer.reply()).doesNotContainIgnoringCase("authorization");
		assertThat(answer.reply()).doesNotContainIgnoringCase("unable to change");
		assertThat(answer.reply()).doesNotContain("attentionCounts");
		assertThat(answer.reply()).doesNotContain("ATTENTION_STATUSES");
		assertThat(answer.reply()).doesNotContain("12");
		assertThat(answer.timing().path()).isEqualTo("deterministic_orders_attention");
		verifyNoInteractions(adminInventoryFacade);
	}

	@Test
	void emptyInventoryDoesNotInventSkus() {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("kind", "low_stock");
		result.put("threshold", 5);
		result.put("availableDefinition", "available = quantity_on_hand - quantity_reserved");
		result.put("items", java.util.List.of());
		Map<String, Object> envelope = ChatToolEnvelope.success("getLowStockProducts", result, 0, 3L);
		when(adminInventoryFacade.execute(AdminInventoryQueryKind.LOW_STOCK)).thenReturn(envelope);

		DeterministicAnswer answer = service.answer(AdminAnalyticsQueryKind.INVENTORY_LOW, ChatLanguage.EN);

		assertThat(answer.reply()).containsIgnoringCase("No products match");
		assertThat(answer.reply()).doesNotContain("SKU ABC");
		assertThat(answer.reply()).doesNotContain("Product A");
	}

	@Test
	void salesSummaryFormatsVerifiedTotals() {
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("from", "2026-09-29T00:00:00Z");
		summary.put("to", "2026-10-29T00:00:00Z");
		summary.put("currency", "VND");
		summary.put("orderCount", 56L);
		summary.put("revenueTotal", new BigDecimal("219737000.00"));
		summary.put("revenueStatuses", List.of("PAID", "PROCESSING", "SHIPPED", "COMPLETED"));
		summary.put("empty", false);
		when(adminAnalyticsService.salesSummary(null, null)).thenReturn(summary);

		DeterministicAnswer answer = service.answer(
				AdminIntentMatch.of(AdminAnalyticsQueryKind.SALES_SUMMARY),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).contains("56");
		assertThat(answer.reply()).contains("219737000");
		assertThat(answer.reply()).contains("VND");
		assertThat(answer.reply()).doesNotContain("getSalesSummary");
		assertThat(answer.timing().path()).isEqualTo("deterministic_sales");
	}

	@Test
	void paidOrderListUsesSearchResults() {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", UUID.fromString("11111111-1111-1111-1111-111111111111"));
		row.put("status", "PAID");
		row.put("totalAmount", new BigDecimal("500000"));
		row.put("currency", "VND");
		row.put("placedAt", "2026-10-01T00:00:00Z");
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("totalMatching", 23L);
		body.put("items", List.of(row));
		body.put("empty", false);
		when(adminAnalyticsService.searchOrders(
				eq(OrderStatus.PAID), isNull(), isNull(), eq(true), eq(true), isNull(), isNull(), anyInt()
		)).thenReturn(body);

		DeterministicAnswer answer = service.answer(
				AdminIntentMatch.listByStatus(OrderStatus.PAID),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).contains("23");
		assertThat(answer.reply()).contains("PAID");
		assertThat(answer.reply()).contains("500000");
		assertThat(answer.timing().path()).isEqualTo("deterministic_orders_by_status");
	}

	@Test
	void emptyPriceFilterDoesNotInventOrders() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("totalMatching", 0L);
		body.put("items", List.of());
		body.put("empty", true);
		when(adminAnalyticsService.searchOrders(
				isNull(), eq(new BigDecimal("999999999")), isNull(), eq(false), eq(true), isNull(), isNull(), anyInt()
		)).thenReturn(body);

		DeterministicAnswer answer = service.answer(
				AdminIntentMatch.listByPrice(new BigDecimal("999999999"), null, false, true),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).containsIgnoringCase("No orders match");
		assertThat(answer.reply()).doesNotContain("11111111");
	}

	@Test
	void clarifyDoesNotCallAnalytics() {
		DeterministicAnswer answer = service.answer(
				AdminIntentMatch.clarify("Please clarify VND vs USD.", "Làm rõ VND hay USD."),
				ChatLanguage.EN
		);
		assertThat(answer.reply()).contains("VND");
		assertThat(answer.timing().path()).isEqualTo("deterministic_clarify");
		verifyNoInteractions(adminAnalyticsService);
	}
}
