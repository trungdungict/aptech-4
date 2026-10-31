package com.aptech.aptech4.seed;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exact natural keys owned by the demo seed catalog. Reset/delete and retired purge use these
 * lists only — never a loose {@code LIKE 'DEMO-%'} heuristic for users/categories.
 */
public final class DemoSeedMarkers {

	/** Extra customer used by journey fixtures (Vietnamese demo shopper). */
	public static final String SECONDARY_CUSTOMER_EMAIL = "lan.nguyen@aptech4.local";

	/**
	 * Former non-FoodMart category slugs. No longer seeded; purged on local demo seed/reset.
	 */
	private static final List<String> RETIRED_CATEGORY_SLUGS = List.of(
			"demo-electronics",
			"demo-books",
			"demo-home",
			"demo-archive"
	);

	/**
	 * Former non-FoodMart product SKUs. No longer seeded; purged on local demo seed/reset.
	 */
	private static final List<String> RETIRED_PRODUCT_SKUS = List.of(
			"DEMO-ELEC-PHONE",
			"DEMO-ELEC-EARBUDS",
			"DEMO-ELEC-CHARGER",
			"DEMO-ELEC-STAND",
			"DEMO-BOOK-JAVA",
			"DEMO-BOOK-SQL",
			"DEMO-BOOK-ALGO",
			"DEMO-HOME-MUG",
			"DEMO-HOME-LAMP",
			"DEMO-HOME-OOS",
			"DEMO-ELEC-OLD",
			"DEMO-ARCH-01"
	);

	private DemoSeedMarkers() {
	}

	public static List<String> categorySlugs() {
		return FoodMartCatalog.categories().stream()
				.map(DemoSeedCatalog.CategoryFixture::slug)
				.toList();
	}

	public static List<String> productSkus() {
		return FoodMartCatalog.products().stream()
				.map(DemoSeedCatalog.ProductFixture::sku)
				.toList();
	}

	public static List<String> retiredCategorySlugs() {
		return RETIRED_CATEGORY_SLUGS;
	}

	public static List<String> retiredProductSkus() {
		return RETIRED_PRODUCT_SKUS;
	}

	/** Active FoodMart keys plus retired legacy keys (reset / purge scope). */
	public static List<String> resetCategorySlugs() {
		return mergeUnique(categorySlugs(), retiredCategorySlugs());
	}

	/** Active FoodMart keys plus retired legacy keys (reset / purge scope). */
	public static List<String> resetProductSkus() {
		return mergeUnique(productSkus(), retiredProductSkus());
	}

	public static List<String> extraCustomerEmails() {
		return List.of(SECONDARY_CUSTOMER_EMAIL);
	}

	private static List<String> mergeUnique(List<String> primary, List<String> secondary) {
		Set<String> merged = new LinkedHashSet<>(primary);
		merged.addAll(secondary);
		return List.copyOf(new ArrayList<>(merged));
	}
}
