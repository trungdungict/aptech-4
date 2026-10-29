package com.aptech.aptech4.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

	@Test
	void orderStatusesMatchDatabaseCheckConstraint() {
		assertEquals(
				java.util.Set.of(
						"PENDING_PAYMENT", "PAID", "PROCESSING", "SHIPPED", "COMPLETED", "CANCELLED", "FAILED"),
				java.util.Arrays.stream(OrderStatus.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet())
		);
	}

	@Test
	void paymentStatusesMatchDatabaseCheckConstraint() {
		assertEquals(
				java.util.Set.of("PENDING", "SUCCEEDED", "FAILED", "CANCELLED"),
				java.util.Arrays.stream(PaymentStatus.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet())
		);
	}
}
