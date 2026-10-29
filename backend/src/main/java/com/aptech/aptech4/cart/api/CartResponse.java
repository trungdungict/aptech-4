package com.aptech.aptech4.cart.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authenticated customer's cart")
public record CartResponse(
		@Schema(description = "Cart id") UUID id,
		@Schema(description = "Line items") List<CartItemResponse> items,
		@Schema(description = "Sum of line subtotals (available and unavailable lines)", type = "number", format = "decimal")
		BigDecimal subtotal,
		@Schema(description = "Currency for monetary fields", example = "VND") String currency,
		@Schema(description = "True when at least one line is not currently purchasable") boolean hasUnavailableItems
) {
}
