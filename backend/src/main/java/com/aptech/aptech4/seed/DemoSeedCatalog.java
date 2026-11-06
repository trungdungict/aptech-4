package com.aptech.aptech4.seed;

import java.math.BigDecimal;
import java.util.List;

/**
 * Shared fixture record types for demo catalog seeding.
 * <p>
 * Active grocery catalog data lives in {@code db/demo/foodmart-catalog.json}
 * ({@link FoodMartCatalog}). Canonical base columns are Vietnamese; English lives in
 * translation rows unless {@link ProductFixture#omitEnglishTranslation()} is true.
 */
public final class DemoSeedCatalog {

	private DemoSeedCatalog() {
	}

	public record CategoryFixture(
			String key,
			String name,
			String slug,
			String description,
			String nameEn,
			String descriptionEn,
			boolean active
	) {
	}

	public record ProductFixture(
			String key,
			String categoryKey,
			String sku,
			String name,
			String description,
			String nameEn,
			String descriptionEn,
			BigDecimal unitPrice,
			boolean active,
			int onHand,
			boolean omitEnglishTranslation
	) {
	}

	/** Empty — FoodMart is the sole seeded catalog. */
	public static final List<CategoryFixture> CATEGORIES = List.of();

	/** Empty — FoodMart is the sole seeded catalog. */
	public static final List<ProductFixture> PRODUCTS = List.of();
}
