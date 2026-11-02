package com.aptech.aptech4.catalog.api;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminInventoryListItemResponse", description = """
		Admin inventory list row. available = quantityOnHand - quantityReserved when hasInventoryRow;
		products without an inventory row report zeros and hasInventoryRow=false (not invented stock).
		""")
public record AdminInventoryListItemResponse(
		@Schema(description = "Product id") UUID productId,
		@Schema(description = "SKU") String sku,
		@Schema(description = "Product name") String name,
		@Schema(description = "Whether product.active is true") boolean active,
		@Schema(description = "Category id") UUID categoryId,
		@Schema(description = "Category name") String categoryName,
		@Schema(description = "True when an inventory row exists for this product") boolean hasInventoryRow,
		@Schema(description = "Units on hand (0 when no inventory row)") int quantityOnHand,
		@Schema(description = "Units soft-held (0 when no inventory row); not editable via list APIs") int quantityReserved,
		@Schema(description = "quantityOnHand - quantityReserved (0 when no inventory row)") int available,
		@Schema(description = "Inventory row updatedAt, or null when no inventory row") Instant updatedAt
) {
}
