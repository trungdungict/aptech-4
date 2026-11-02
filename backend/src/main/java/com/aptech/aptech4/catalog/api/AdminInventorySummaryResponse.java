package com.aptech.aptech4.catalog.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminInventorySummaryResponse", description = """
		Catalog-wide inventory aggregates. Definitions match list filters:
		OUT = available <= 0; LOW = 0 < available <= threshold; IN_STOCK = available > threshold.
		available uses coalesce(on_hand,0)-coalesce(reserved,0); missing inventory row → available 0.
		""")
public record AdminInventorySummaryResponse(
		@Schema(description = "Total catalog products (active + inactive)") long totalProducts,
		@Schema(description = "Products with available > threshold") long inStockCount,
		@Schema(description = "Products with 0 < available <= threshold") long lowStockCount,
		@Schema(description = "Products with available <= 0 (includes missing inventory rows)") long outOfStockCount,
		@Schema(description = "Configured low-stock threshold used for LOW / IN_STOCK split") int lowStockThreshold,
		@Schema(description = "Human-readable available formula") String availableDefinition
) {
}
