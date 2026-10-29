package com.aptech.aptech4.catalog.api;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Catalog product (public responses are always active=true)")
public record ProductResponse(
		@Schema(description = "Product id") UUID id,
		@Schema(description = "Stock-keeping unit") String sku,
		@Schema(description = "Display name") String name,
		@Schema(description = "Description; may be null") String description,
		@Schema(description = "Unit price", type = "number", format = "decimal") BigDecimal unitPrice,
		@Schema(description = "ISO-4217 currency code", example = "VND") String currency,
		@Schema(description = "Parent category id") UUID categoryId,
		@Schema(description = "Parent category name") String categoryName,
		@Schema(description = "Whether the product is active") boolean active,
		@Schema(description = "Image URL when available; null until media is modeled", nullable = true) String imageUrl,
		@Schema(description = "true if inventory quantity_on_hand > 0; false if 0; null if no inventory row", nullable = true)
		Boolean inStock
) implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;
}
