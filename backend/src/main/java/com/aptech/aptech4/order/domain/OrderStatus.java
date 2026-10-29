package com.aptech.aptech4.order.domain;

/**
 * Values allowed by {@code chk_orders_status}.
 */
public enum OrderStatus {
	PENDING_PAYMENT,
	PAID,
	PROCESSING,
	SHIPPED,
	COMPLETED,
	CANCELLED,
	FAILED
}
