package com.aptech.aptech4.catalog.api;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminProductInventoryResponse")
public record AdminProductInventoryResponse(
		@Schema(description = "Product id (inventory is 1:1 with product)") UUID productId,
		@Schema(description = "Physical stock on hand") int quantityOnHand,
		@Schema(description = "Units soft-held for open checkouts (ADR-006)") int quantityReserved,
		@Schema(description = "Sellable units = onHand - reserved") int available,
		@Schema(description = "Last inventory update time") Instant updatedAt
) {
}
