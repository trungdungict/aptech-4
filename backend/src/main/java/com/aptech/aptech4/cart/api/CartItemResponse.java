package com.aptech.aptech4.cart.api;

import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cart line with server-side current product pricing")
public record CartItemResponse(
		@Schema(description = "Cart item id") UUID id,
		@Schema(description = "Product id") UUID productId,
		@Schema(description = "Current product name") String productName,
		@Schema(description = "Image URL when available; null until media is modeled", nullable = true) String imageUrl,
		@Schema(description = "Quantity") int quantity,
		@Schema(description = "Current unit price from product", type = "number", format = "decimal") BigDecimal unitPrice,
		@Schema(description = "quantity × unitPrice", type = "number", format = "decimal") BigDecimal lineSubtotal,
		@Schema(description = "Currency code", example = "VND") String currency,
		@Schema(description = "Whether the product (and its category) is currently purchasable") boolean available,
		@Schema(description = "Whether products.active is true") boolean productActive
) {
}
