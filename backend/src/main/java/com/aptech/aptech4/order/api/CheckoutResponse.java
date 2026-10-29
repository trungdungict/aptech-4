package com.aptech.aptech4.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Checkout result (order + payment). Mock payment only — not a real charge.")
public record CheckoutResponse(
		UUID orderId,
		String orderStatus,
		BigDecimal subtotalAmount,
		BigDecimal shippingAmount,
		BigDecimal totalAmount,
		String currency,
		Instant placedAt,
		List<CheckoutOrderItemResponse> items,
		CheckoutPaymentResponse payment,
		boolean cartCleared
) {
	public record CheckoutOrderItemResponse(
			UUID productId,
			String productSku,
			String productName,
			BigDecimal unitPrice,
			int quantity,
			BigDecimal lineTotal
	) {
	}

	public record CheckoutPaymentResponse(
			UUID paymentId,
			String provider,
			String status,
			BigDecimal amount,
			String currency,
			String providerReference,
			String failureReason
	) {
	}
}
