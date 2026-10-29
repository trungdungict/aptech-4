package com.aptech.aptech4.seed;

import java.util.List;

/**
 * Exact natural keys owned by the demo seed catalog. Reset/delete scope uses these lists only —
 * never a loose {@code LIKE 'DEMO-%'} heuristic for users/categories.
 */
public final class DemoSeedMarkers {

	private DemoSeedMarkers() {
	}

	public static List<String> categorySlugs() {
		return DemoSeedCatalog.CATEGORIES.stream().map(DemoSeedCatalog.CategoryFixture::slug).toList();
	}

	public static List<String> productSkus() {
		return DemoSeedCatalog.PRODUCTS.stream().map(DemoSeedCatalog.ProductFixture::sku).toList();
	}
}
