package com.aptech.aptech4.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AdminProductInventoryUpdateRequest")
public record AdminProductInventoryUpdateRequest(
		@NotNull
		@Min(value = 0, message = "quantityOnHand must be zero or greater")
		@Schema(description = "Absolute quantity on hand. Must be >= current quantityReserved.", example = "25")
		Integer quantityOnHand
) {
}
