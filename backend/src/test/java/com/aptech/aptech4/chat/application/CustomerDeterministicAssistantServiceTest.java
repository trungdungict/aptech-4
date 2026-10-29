package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.aptech.aptech4.catalog.api.ProductResponse;
import com.aptech.aptech4.catalog.application.ProductService;
import com.aptech.aptech4.chat.api.ChatLanguage;
import com.aptech.aptech4.chat.application.CustomerDeterministicAssistantService.DeterministicAnswer;
import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.common.api.PageResponse;
import com.aptech.aptech4.identity.security.JwtService;
import com.aptech.aptech4.identity.security.SecurityRoles;
import com.aptech.aptech4.order.api.OrderResponse;
import com.aptech.aptech4.order.application.OrderService;
import com.aptech.aptech4.order.domain.OrderStatus;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CustomerDeterministicAssistantServiceTest {

	@Mock
	OrderService orderService;

	@Mock
	ProductService productService;

	CustomerDeterministicAssistantService service;

	UUID customerId;

	@BeforeEach
	void setUp() {
		service = new CustomerDeterministicAssistantService(orderService, productService);
		customerId = UUID.randomUUID();
		var principal = new JwtService.AccessTokenPrincipal(customerId, "c@example.com", List.of("CUSTOMER"));
		var auth = new UsernamePasswordAuthenticationToken(
				principal, null, SecurityRoles.authorities(principal.roles())
		);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void latestOrderUsesAuthenticatedCustomerListMine() {
		UUID orderId = UUID.randomUUID();
		OrderResponse summary = order(orderId, "PAID", "150000");
		OrderResponse detail = order(orderId, "PAID", "150000");
		when(orderService.listMine(isNull(), eq(0), eq(1), eq("placedAt,desc")))
				.thenReturn(PageResponse.of(List.of(summary), 0, 1, 1));
		when(orderService.getMine(orderId)).thenReturn(detail);

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.of(CustomerIntentKind.LATEST_ORDER),
				ChatLanguage.EN
		);

		assertThat(answer).isNotNull();
		assertThat(answer.reply()).contains("PAID");
		assertThat(answer.reply()).contains("150000");
		assertThat(answer.reply()).contains("/orders/" + orderId);
		assertThat(answer.reply()).doesNotContainIgnoringCase("order id");
		assertThat(answer.reply()).doesNotContainIgnoringCase("provide");
		assertThat(answer.timing().path()).isEqualTo("deterministic_latest_order");
		verify(orderService).listMine(isNull(), eq(0), eq(1), eq("placedAt,desc"));
		verify(orderService).getMine(orderId);
		verifyNoInteractions(productService);
	}

	@Test
	void latestOrderNoOrdersMessage() {
		when(orderService.listMine(isNull(), eq(0), eq(1), eq("placedAt,desc")))
				.thenReturn(PageResponse.of(List.of(), 0, 1, 0));

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.of(CustomerIntentKind.LATEST_ORDER),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).containsIgnoringCase("do not have any orders");
		verify(orderService, never()).getMine(any());
	}

	@Test
	void latestOrderFailureSurfacesSafeMessage() {
		when(orderService.listMine(isNull(), eq(0), eq(1), eq("placedAt,desc")))
				.thenThrow(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error", "boom"));

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.of(CustomerIntentKind.LATEST_ORDER),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).containsIgnoringCase("Could not load");
		assertThat(answer.reply()).doesNotContain("0");
		assertThat(answer.timing().path()).isEqualTo("deterministic_customer_failure");
	}

	@Test
	void orderCountReturnsAuthenticatedCustomerTotal() {
		UUID orderId = UUID.randomUUID();
		when(orderService.listMine(isNull(), eq(0), eq(1), eq("placedAt,desc")))
				.thenReturn(PageResponse.of(List.of(order(orderId, "PAID", "100")), 0, 1, 7));

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.of(CustomerIntentKind.ORDER_COUNT),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).isEqualTo("You have placed 7 orders.");
		assertThat(answer.reply()).doesNotContainIgnoringCase("jwt");
		assertThat(answer.reply()).doesNotContainIgnoringCase("authentication");
		assertThat(answer.timing().path()).isEqualTo("deterministic_order_count");
		verify(orderService).listMine(isNull(), eq(0), eq(1), eq("placedAt,desc"));
		verify(orderService, never()).getMine(any());
	}

	@Test
	void orderCountZeroOnlyAfterSuccessfulEmptyQuery() {
		when(orderService.listMine(isNull(), eq(0), eq(1), eq("placedAt,desc")))
				.thenReturn(PageResponse.of(List.of(), 0, 1, 0));

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.of(CustomerIntentKind.ORDER_COUNT),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).isEqualTo("You have placed 0 orders.");
		assertThat(answer.timing().path()).isEqualTo("deterministic_order_count");
	}

	@Test
	void orderCountAuthFailureDoesNotReturnZero() {
		when(orderService.listMine(isNull(), eq(0), eq(1), eq("placedAt,desc")))
				.thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required"));

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.of(CustomerIntentKind.ORDER_COUNT),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).containsIgnoringCase("sign in");
		assertThat(answer.reply()).doesNotContain("0");
		assertThat(answer.reply()).doesNotContainIgnoringCase("placed 0");
		assertThat(answer.timing().path()).isEqualTo("deterministic_customer_auth");
	}

	@Test
	void orderCountToolFailureDoesNotReturnZero() {
		when(orderService.listMine(isNull(), eq(0), eq(1), eq("placedAt,desc")))
				.thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Error", "db down"));

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.of(CustomerIntentKind.ORDER_COUNT),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).doesNotContain("0");
		assertThat(answer.reply()).containsIgnoringCase("No order IDs or counts were assumed");
		assertThat(answer.timing().path()).isEqualTo("deterministic_customer_failure");
	}

	@Test
	void paidOrderListUsesStatusFilterForAuthenticatedCustomer() {
		UUID orderId = UUID.randomUUID();
		when(orderService.listMine(eq(OrderStatus.PAID), eq(0), eq(10), eq("placedAt,desc")))
				.thenReturn(PageResponse.of(List.of(order(orderId, "PAID", "320000")), 0, 10, 1));

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.listByStatus(OrderStatus.PAID),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).contains(orderId.toString());
		assertThat(answer.reply()).contains("PAID");
		assertThat(answer.reply()).contains("320000");
		assertThat(answer.reply()).doesNotContainIgnoringCase("permission");
		assertThat(answer.reply()).doesNotContainIgnoringCase("invoke");
		assertThat(answer.timing().path()).isEqualTo("deterministic_orders_by_status");
		verify(orderService).listMine(eq(OrderStatus.PAID), eq(0), eq(10), eq("placedAt,desc"));
	}

	@Test
	void paidOrderListEmptyResultIsClear() {
		when(orderService.listMine(eq(OrderStatus.PAID), eq(0), eq(10), eq("placedAt,desc")))
				.thenReturn(PageResponse.of(List.of(), 0, 10, 0));

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.listByStatus(OrderStatus.PAID),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).isEqualTo("You have no PAID orders.");
		assertThat(answer.reply()).doesNotContainIgnoringCase("jwt");
	}

	@Test
	void paidOrderCountUsesStatusFilter() {
		when(orderService.listMine(eq(OrderStatus.PAID), eq(0), eq(1), eq("placedAt,desc")))
				.thenReturn(PageResponse.of(List.of(order(UUID.randomUUID(), "PAID", "1")), 0, 1, 3));

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.countByStatus(OrderStatus.PAID),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).isEqualTo("You have 3 PAID orders.");
		assertThat(answer.timing().path()).isEqualTo("deterministic_order_count_by_status");
	}

	@Test
	void orderListFailureDoesNotFabricateOrderIds() {
		when(orderService.listMine(eq(OrderStatus.PAID), eq(0), eq(10), eq("placedAt,desc")))
				.thenThrow(new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error", "boom"));

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.listByStatus(OrderStatus.PAID),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).doesNotContainIgnoringCase("invoke");
		assertThat(answer.reply()).containsIgnoringCase("No order IDs");
		assertThat(answer.timing().path()).isEqualTo("deterministic_customer_failure");
	}

	@Test
	void adminAnalyticsForbiddenDoesNotAskForJwt() {
		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.of(CustomerIntentKind.ADMIN_ANALYTICS_FORBIDDEN),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).isEqualTo(
				"Store-wide order analytics are available to administrators only."
		);
		assertThat(answer.reply()).doesNotContainIgnoringCase("jwt");
		assertThat(answer.reply()).doesNotContainIgnoringCase("sign in");
		assertThat(answer.timing().path()).isEqualTo("deterministic_customer_forbidden");
		verifyNoInteractions(orderService, productService);
	}

	@Test
	void productsUnderFiltersInStockAndMaxPrice() {
		UUID productId = UUID.randomUUID();
		ProductResponse product = new ProductResponse(
				productId,
				"MUG-1",
				"Demo Ceramic Mug",
				"desc",
				new BigDecimal("120000"),
				"VND",
				UUID.randomUUID(),
				"Home",
				true,
				null,
				true
		);
		when(productService.listPublic(
				isNull(),
				isNull(),
				isNull(),
				eq(new BigDecimal("2000000")),
				eq(Boolean.TRUE),
				eq(0),
				eq(10),
				eq("name,asc")
		)).thenReturn(PageResponse.of(List.of(product), 0, 10, 1));

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.productsUnder(new BigDecimal("2000000")),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).contains("Demo Ceramic Mug");
		assertThat(answer.reply()).contains("120000");
		assertThat(answer.reply()).contains("1. ");
		assertThat(answer.reply()).contains("/products/" + productId);
		assertThat(answer.timing().path()).isEqualTo("deterministic_products_under");
		verifyNoInteractions(orderService);
	}

	@Test
	void checkoutHelpMentionsRequiredAddressWithoutSimulateFailure() {
		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.of(CustomerIntentKind.CHECKOUT_HELP),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).containsIgnoringCase("address");
		assertThat(answer.reply()).contains("/checkout");
		assertThat(answer.reply()).contains("/cart");
		assertThat(answer.reply()).doesNotContain("simulateFailure");
		assertThat(answer.timing().path()).isEqualTo("deterministic_checkout_help");
		verifyNoInteractions(orderService, productService);
	}

	@Test
	void unauthorizedWithoutPrincipalReturnsAuthMessageNotZero() {
		SecurityContextHolder.clearContext();

		DeterministicAnswer answer = service.answer(
				CustomerIntentMatch.of(CustomerIntentKind.ORDER_COUNT),
				ChatLanguage.EN
		);

		assertThat(answer.reply()).containsIgnoringCase("sign in");
		assertThat(answer.reply()).doesNotContain("0");
		assertThat(answer.timing().path()).isEqualTo("deterministic_customer_auth");
		verifyNoInteractions(orderService);
	}

	private static OrderResponse order(UUID id, String status, String total) {
		return new OrderResponse(
				id,
				UUID.randomUUID(),
				status,
				new BigDecimal(total),
				BigDecimal.ZERO,
				new BigDecimal(total),
				"VND",
				Instant.parse("2026-01-01T00:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"),
				null,
				List.of(),
				null
		);
	}
}
