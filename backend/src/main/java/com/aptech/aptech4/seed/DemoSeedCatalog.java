package com.aptech.aptech4.seed;

import java.math.BigDecimal;
import java.util.List;

/**
 * Shared fixture record types for demo catalog seeding.
 * <p>
 * Active grocery catalog data lives in {@code db/demo/foodmart-catalog.json}
 * ({@link FoodMartCatalog}). Legacy non-FoodMart {@code DEMO-*} fixtures were removed;
 * their natural keys remain in {@link DemoSeedMarkers#retiredProductSkus()} /
 * {@link DemoSeedMarkers#retiredCategorySlugs()} for local-demo reconciliation only.
 */
public final class DemoSeedCatalog {

	private DemoSeedCatalog() {
	}

	public record CategoryFixture(String key, String name, String slug, String description, boolean active) {
	}

	public record ProductFixture(
			String key,
			String categoryKey,
			String sku,
			String name,
			String description,
			BigDecimal unitPrice,
			boolean active,
			int onHand
	) {
	}

	/** Empty — FoodMart is the sole seeded catalog. */
	public static final List<CategoryFixture> CATEGORIES = List.of();

	/** Empty — FoodMart is the sole seeded catalog. */
	public static final List<ProductFixture> PRODUCTS = List.of();
}
