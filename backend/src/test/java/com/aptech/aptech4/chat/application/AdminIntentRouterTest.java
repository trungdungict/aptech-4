package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.aptech.aptech4.order.domain.OrderStatus;

class AdminIntentRouterTest {

	@Test
	void attentionCount() {
		assertThat(AdminIntentRouter.route("How many orders need processing?").kind())
				.isEqualTo(AdminAnalyticsQueryKind.ORDERS_NEEDING_ATTENTION);
		assertThat(AdminIntentRouter.route("Có bao nhiêu đơn đang chờ xử lý?").kind())
				.isEqualTo(AdminAnalyticsQueryKind.ORDERS_NEEDING_ATTENTION);
	}

	@Test
	void salesSummaryVariants() {
		assertThat(AdminIntentRouter.route("Summarize sales for the last 30 days.").kind())
				.isEqualTo(AdminAnalyticsQueryKind.SALES_SUMMARY);
		assertThat(AdminIntentRouter.route("sales summary").kind())
				.isEqualTo(AdminAnalyticsQueryKind.SALES_SUMMARY);
		assertThat(AdminIntentRouter.route("Tóm tắt doanh thu 30 ngày gần đây.").kind())
				.isEqualTo(AdminAnalyticsQueryKind.SALES_SUMMARY);
	}

	@Test
	void listPaidOrders() {
		AdminIntentMatch match = AdminIntentRouter.route("list up orders has been paid");
		assertThat(match.kind()).isEqualTo(AdminAnalyticsQueryKind.ORDER_LIST_BY_STATUS);
		assertThat(match.status()).isEqualTo(OrderStatus.PAID);

		assertThat(AdminIntentRouter.route("Show paid orders.").status()).isEqualTo(OrderStatus.PAID);
		assertThat(AdminIntentRouter.route("Đơn hàng nào đã thanh toán?").status()).isEqualTo(OrderStatus.PAID);
		assertThat(AdminIntentRouter.route("List orders that have been paid.").status()).isEqualTo(OrderStatus.PAID);
	}

	@Test
	void listByPriceGreaterThan() {
		AdminIntentMatch match = AdminIntentRouter.route("order price > 320000");
		assertThat(match.kind()).isEqualTo(AdminAnalyticsQueryKind.ORDER_LIST_BY_PRICE);
		assertThat(match.minTotal()).isEqualByComparingTo("320000");
		assertThat(match.maxTotal()).isNull();
		assertThat(match.minInclusive()).isFalse();

		AdminIntentMatch en = AdminIntentRouter.route("List orders with a total greater than 320000.");
		assertThat(en.minTotal()).isEqualByComparingTo("320000");
		assertThat(en.minInclusive()).isFalse();

		AdminIntentMatch vi = AdminIntentRouter.route("Liệt kê đơn hàng có giá trị trên 320.000 đồng.");
		assertThat(vi.kind()).isEqualTo(AdminAnalyticsQueryKind.ORDER_LIST_BY_PRICE);
		assertThat(vi.minTotal()).isEqualByComparingTo("320000");
		assertThat(vi.minInclusive()).isFalse();
	}

	@Test
	void usdWithoutVndAsksClarification() {
		AdminIntentMatch match = AdminIntentRouter.route("Show orders over $320000");
		assertThat(match.kind()).isEqualTo(AdminAnalyticsQueryKind.CLARIFY);
		assertThat(match.clarifyMessageEn()).containsIgnoringCase("VND");
	}

	@Test
	void doesNotTreatListAsCount() {
		assertThat(AdminIntentRouter.route("list up orders has been paid").kind())
				.isNotEqualTo(AdminAnalyticsQueryKind.ORDER_COUNTS_BY_STATUS);
		assertThat(AdminIntentRouter.route("order price > 320000").kind())
				.isNotEqualTo(AdminAnalyticsQueryKind.ORDER_COUNTS_BY_STATUS);
	}

	@Test
	void parseAmountFormats() {
		assertThat(AdminIntentRouter.parseAmount("320000", false)).contains(new BigDecimal("320000"));
		assertThat(AdminIntentRouter.parseAmount("320,000 VND", true)).contains(new BigDecimal("320000"));
		assertThat(AdminIntentRouter.parseAmount("320.000 đồng", true)).contains(new BigDecimal("320000"));
	}

	@Test
	void inventoryAndGeneral() {
		assertThat(AdminIntentRouter.route("Which products are low on stock?").kind())
				.isEqualTo(AdminAnalyticsQueryKind.INVENTORY_LOW);
		assertThat(AdminIntentRouter.route("Hello").kind()).isEqualTo(AdminAnalyticsQueryKind.NONE);
	}
}
