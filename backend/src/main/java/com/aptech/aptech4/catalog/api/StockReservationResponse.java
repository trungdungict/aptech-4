package com.aptech.aptech4.catalog.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stock reservation (soft hold). Confirm is service-only for checkout.")
public record StockReservationResponse(
		UUID id,
		UUID userId,
		String idempotencyKey,
		String status,
		Instant expiresAt,
		List<StockReservationItemResponse> items
) {
	public record StockReservationItemResponse(
			UUID productId,
			int quantity
	) {
	}
}
