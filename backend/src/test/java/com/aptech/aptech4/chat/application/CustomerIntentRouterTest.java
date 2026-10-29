package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.aptech.aptech4.order.domain.OrderStatus;

class CustomerIntentRouterTest {

	@Test
	void routesOrderCountForPlacedOrdersPrompt() {
		assertThat(CustomerIntentRouter.route("How many orders have I placed?").kind())
				.isEqualTo(CustomerIntentKind.ORDER_COUNT);
		assertThat(CustomerIntentRouter.route("How many orders did I place?").kind())
				.isEqualTo(CustomerIntentKind.ORDER_COUNT);
		assertThat(CustomerIntentRouter.route("my order count").kind())
				.isEqualTo(CustomerIntentKind.ORDER_COUNT);
		assertThat(CustomerIntentRouter.route("Tôi đã đặt bao nhiêu đơn hàng?").kind())
				.isEqualTo(CustomerIntentKind.ORDER_COUNT);
	}

	@Test
	void routesPaidOrderListAndCount() {
		CustomerIntentMatch list = CustomerIntentRouter.route("List my paid orders.");
		assertThat(list.kind()).isEqualTo(CustomerIntentKind.ORDER_LIST_BY_STATUS);
		assertThat(list.orderStatus()).isEqualTo(OrderStatus.PAID);

		CustomerIntentMatch processing = CustomerIntentRouter.route("Show my processing orders.");
		assertThat(processing.kind()).isEqualTo(CustomerIntentKind.ORDER_LIST_BY_STATUS);
		assertThat(processing.orderStatus()).isEqualTo(OrderStatus.PROCESSING);

		CustomerIntentMatch completed = CustomerIntentRouter.route("List my completed orders.");
		assertThat(completed.kind()).isEqualTo(CustomerIntentKind.ORDER_LIST_BY_STATUS);
		assertThat(completed.orderStatus()).isEqualTo(OrderStatus.COMPLETED);

		CustomerIntentMatch paidCount = CustomerIntentRouter.route("How many paid orders do I have?");
		assertThat(paidCount.kind()).isEqualTo(CustomerIntentKind.ORDER_COUNT_BY_STATUS);
		assertThat(paidCount.orderStatus()).isEqualTo(OrderStatus.PAID);
	}

	@Test
	void routesStoreWideAnalyticsAsAdminForbiddenForCustomer() {
		assertThat(CustomerIntentRouter.route(
				"How many orders are currently being processed across the entire store?").kind())
				.isEqualTo(CustomerIntentKind.ADMIN_ANALYTICS_FORBIDDEN);
		assertThat(CustomerIntentRouter.route("How many orders need processing?").kind())
				.isEqualTo(CustomerIntentKind.ADMIN_ANALYTICS_FORBIDDEN);
	}

	@Test
	void routesLatestOrderIncludingStatusAndTotalPrompt() {
		assertThat(CustomerIntentRouter.route(
				"What is the status and total amount of my latest order?").kind())
				.isEqualTo(CustomerIntentKind.LATEST_ORDER);
		assertThat(CustomerIntentRouter.route("status of my latest order").kind())
				.isEqualTo(CustomerIntentKind.LATEST_ORDER);
		assertThat(CustomerIntentRouter.route("Đơn hàng gần nhất của tôi?").kind())
				.isEqualTo(CustomerIntentKind.LATEST_ORDER);
	}

	@Test
	void routesProductsUnderWithParsedPrice() {
		CustomerIntentMatch match = CustomerIntentRouter.route("Show available products under 2000000.");
		assertThat(match.kind()).isEqualTo(CustomerIntentKind.PRODUCT_UNDER_PRICE);
		assertThat(match.maxPrice()).isEqualByComparingTo(new BigDecimal("2000000"));
	}

	@Test
	void routesCheckoutHelp() {
		assertThat(CustomerIntentRouter.route("How do I complete checkout?").kind())
				.isEqualTo(CustomerIntentKind.CHECKOUT_HELP);
		assertThat(CustomerIntentRouter.route("Tôi checkout thế nào?").kind())
				.isEqualTo(CustomerIntentKind.CHECKOUT_HELP);
	}

	@Test
	void leavesUnrelatedMessagesAlone() {
		assertThat(CustomerIntentRouter.route("Hello").kind()).isEqualTo(CustomerIntentKind.NONE);
		assertThat(CustomerIntentRouter.route("What is the weather?").kind())
				.isEqualTo(CustomerIntentKind.NONE);
	}
}
