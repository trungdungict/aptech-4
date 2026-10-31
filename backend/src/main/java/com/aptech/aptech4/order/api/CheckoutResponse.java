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
		BigDecimal discountAmount,
		BigDecimal totalAmount,
		String currency,
		Instant placedAt,
		List<CheckoutOrderItemResponse> items,
		List<AppliedPromotionResponse> appliedPromotions,
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

	@Schema(description = "Applied promotion at checkout (ADR-020 D7).")
	public record AppliedPromotionResponse(
			UUID campaignId,
			String campaignName,
			String strategyType,
			BigDecimal discountAmount
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
