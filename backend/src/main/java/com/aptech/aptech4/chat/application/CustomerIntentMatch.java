package com.aptech.aptech4.chat.application;

import java.math.BigDecimal;

import com.aptech.aptech4.order.domain.OrderStatus;

public record CustomerIntentMatch(
		CustomerIntentKind kind,
		BigDecimal maxPrice,
		OrderStatus orderStatus
) {
	public static CustomerIntentMatch of(CustomerIntentKind kind) {
		return new CustomerIntentMatch(kind, null, null);
	}

	public static CustomerIntentMatch productsUnder(BigDecimal maxPrice) {
		return new CustomerIntentMatch(CustomerIntentKind.PRODUCT_UNDER_PRICE, maxPrice, null);
	}

	public static CustomerIntentMatch listByStatus(OrderStatus status) {
		return new CustomerIntentMatch(CustomerIntentKind.ORDER_LIST_BY_STATUS, null, status);
	}

	public static CustomerIntentMatch countByStatus(OrderStatus status) {
		return new CustomerIntentMatch(CustomerIntentKind.ORDER_COUNT_BY_STATUS, null, status);
	}

	public static CustomerIntentMatch none() {
		return of(CustomerIntentKind.NONE);
	}
}
