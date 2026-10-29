package com.aptech.aptech4.order.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.order.domain.OrderStatus;

class OrderStatusTransitionsTest {

	@Test
	void adminHappyPathAndTerminals() {
		assertThatCode(() -> OrderStatusTransitions.assertAdminTransition(OrderStatus.PAID, OrderStatus.PROCESSING))
			.doesNotThrowAnyException();
		assertThatCode(() -> OrderStatusTransitions.assertAdminTransition(OrderStatus.PROCESSING, OrderStatus.SHIPPED))
			.doesNotThrowAnyException();
		assertThatCode(() -> OrderStatusTransitions.assertAdminTransition(OrderStatus.SHIPPED, OrderStatus.COMPLETED))
			.doesNotThrowAnyException();

		assertThatThrownBy(() -> OrderStatusTransitions.assertAdminTransition(OrderStatus.COMPLETED, OrderStatus.SHIPPED))
			.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> OrderStatusTransitions.assertAdminTransition(OrderStatus.PAID, OrderStatus.SHIPPED))
			.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> OrderStatusTransitions.assertAdminTransition(OrderStatus.FAILED, OrderStatus.PAID))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void customerCancelOnlyPendingPayment() {
		assertThatCode(() -> OrderStatusTransitions.assertCustomerCancel(OrderStatus.PENDING_PAYMENT))
			.doesNotThrowAnyException();
		assertThatThrownBy(() -> OrderStatusTransitions.assertCustomerCancel(OrderStatus.PAID))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("PAID");
	}
}
