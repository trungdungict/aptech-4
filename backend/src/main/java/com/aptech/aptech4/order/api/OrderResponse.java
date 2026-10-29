package com.aptech.aptech4.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Order with item price snapshots (not live catalog prices)")
public record OrderResponse(
		UUID id,
		UUID userId,
		String status,
		BigDecimal subtotalAmount,
		BigDecimal shippingAmount,
		BigDecimal totalAmount,
		String currency,
		Instant placedAt,
		Instant updatedAt,
		OrderShippingResponse shipping,
		List<OrderItemResponse> items,
		OrderPaymentSummaryResponse payment
) {
	public record OrderShippingResponse(
			String recipientName,
			String line1,
			String line2,
			String city,
			String stateRegion,
			String postalCode,
			String countryCode,
			String phone
	) {
	}

	public record OrderItemResponse(
			UUID productId,
			String productSku,
			String productName,
			BigDecimal unitPrice,
			int quantity,
			BigDecimal lineTotal
	) {
	}

	@Schema(description = "Payment summary; mock provider only — not a real card charge")
	public record OrderPaymentSummaryResponse(
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
