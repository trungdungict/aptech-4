package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.aptech.aptech4.order.domain.OrderStatus;

class CustomerIntentRouterTest {

	@ParameterizedTest(name = "ORDER_COUNT matched: {0}")
	@MethodSource("orderCountMatched")
	void routesOrderCountForNaturalLanguageVariants(String prompt) {
		assertThat(CustomerIntentRouter.route(prompt).kind())
				.isEqualTo(CustomerIntentKind.ORDER_COUNT);
	}

	static Stream<Arguments> orderCountMatched() {
		return Stream.of(
				Arguments.of("How many orders have I placed?"),
				Arguments.of("How many orders did I place?"),
				Arguments.of("how many orders do I have"),
				Arguments.of("How many orders I have?"),
				Arguments.of("What is the total number of my orders?"),
				Arguments.of("total number of my order"),
				Arguments.of("total number of my orders"),
				Arguments.of("number of my orders I have"),
				Arguments.of("number of orders I placed"),
				Arguments.of("my order count"),
				Arguments.of("my total order count"),
				Arguments.of("count of my orders"),
				Arguments.of("count my orders"),
				Arguments.of("my orders total"),
				Arguments.of("Tôi đã đặt bao nhiêu đơn hàng?"),
				Arguments.of("tôi đã đặt bao nhiêu"),
				Arguments.of("Bao nhiêu đơn hàng của tôi?"),
				Arguments.of("tôi có bao nhiêu đơn"),
				Arguments.of("Tổng số đơn hàng"),
				Arguments.of("tổng số đơn"),
				Arguments.of("Số đơn hàng"),
				Arguments.of("đếm đơn hàng")
		);
	}

	@ParameterizedTest(name = "ORDER_COUNT unmatched: {0}")
	@MethodSource("orderCountUnmatched")
	void doesNotRouteOrderCountForUnrelatedOrAdminPhrasing(String prompt, CustomerIntentKind expected) {
		assertThat(CustomerIntentRouter.route(prompt).kind()).isEqualTo(expected);
	}

	static Stream<Arguments> orderCountUnmatched() {
		return Stream.of(
				Arguments.of("Hello", CustomerIntentKind.NONE),
				Arguments.of("What is the weather?", CustomerIntentKind.NONE),
				Arguments.of("Tell me a joke", CustomerIntentKind.NONE),
				Arguments.of("How many orders need processing?", CustomerIntentKind.ADMIN_ANALYTICS_FORBIDDEN),
				Arguments.of(
						"How many orders are currently being processed across the entire store?",
						CustomerIntentKind.ADMIN_ANALYTICS_FORBIDDEN
				),
				Arguments.of("status of my latest order", CustomerIntentKind.LATEST_ORDER),
				Arguments.of("How do I complete checkout?", CustomerIntentKind.CHECKOUT_HELP),
				Arguments.of("Show available products under 2000000.", CustomerIntentKind.PRODUCT_UNDER_PRICE)
		);
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
