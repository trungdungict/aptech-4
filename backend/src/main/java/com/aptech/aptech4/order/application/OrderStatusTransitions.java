package com.aptech.aptech4.order.application;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.aptech.aptech4.common.api.ApiException;
import com.aptech.aptech4.order.domain.OrderStatus;

/**
 * Allowed order status transitions (ADR-008). Payment-driven statuses are not set here.
 */
public final class OrderStatusTransitions {

	private static final Map<OrderStatus, Set<OrderStatus>> ADMIN = new EnumMap<>(OrderStatus.class);
	private static final Set<OrderStatus> CUSTOMER_CANCEL_FROM = EnumSet.of(OrderStatus.PENDING_PAYMENT);

	static {
		ADMIN.put(OrderStatus.PAID, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED));
		ADMIN.put(OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
		ADMIN.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.COMPLETED));
		ADMIN.put(OrderStatus.PENDING_PAYMENT, EnumSet.of(OrderStatus.CANCELLED));
		ADMIN.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
		ADMIN.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
		ADMIN.put(OrderStatus.FAILED, EnumSet.noneOf(OrderStatus.class));
	}

	private OrderStatusTransitions() {
	}

	public static void assertAdminTransition(OrderStatus from, OrderStatus to) {
		if (from == to) {
			return;
		}
		Set<OrderStatus> allowed = ADMIN.getOrDefault(from, EnumSet.noneOf(OrderStatus.class));
		if (!allowed.contains(to)) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"Conflict",
					"Invalid status transition: " + from + " → " + to
			);
		}
		if (to == OrderStatus.PAID || to == OrderStatus.FAILED) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"Conflict",
					"Payment-owned statuses cannot be set via admin status API"
			);
		}
	}

	public static void assertCustomerCancel(OrderStatus from) {
		if (!CUSTOMER_CANCEL_FROM.contains(from)) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"Conflict",
					"Order cannot be cancelled in status " + from
			);
		}
	}
}
