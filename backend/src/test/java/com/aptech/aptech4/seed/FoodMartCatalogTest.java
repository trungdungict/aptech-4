package com.aptech.aptech4.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class FoodMartCatalogTest {

	@Test
	void loadsEightCategoriesAndFortyEightUniqueFoodSkusWithNonNegativeStock() {
		assertThat(FoodMartCatalog.categories()).hasSize(8);
		assertThat(FoodMartCatalog.products()).hasSize(48);
		assertThat(DemoSeedCatalog.CATEGORIES).isEmpty();
		assertThat(DemoSeedCatalog.PRODUCTS).isEmpty();

		Set<String> skus = new HashSet<>();
		Set<String> categoryKeys = new HashSet<>();
		FoodMartCatalog.categories().forEach(c -> {
			assertThat(c.slug()).startsWith("food-");
			assertThat(c.active()).isTrue();
			categoryKeys.add(c.key());
		});

		for (var product : FoodMartCatalog.products()) {
			assertThat(product.sku()).startsWith("FOOD-");
			assertThat(skus.add(product.sku())).isTrue();
			assertThat(categoryKeys).contains(product.categoryKey());
			assertThat(product.onHand()).isGreaterThanOrEqualTo(0);
			assertThat(product.unitPrice()).isGreaterThanOrEqualTo(java.math.BigDecimal.ZERO);
		}

		assertThat(DemoSeedMarkers.productSkus()).contains("FOOD-FRUIT-BANANA-001");
		assertThat(DemoSeedMarkers.productSkus()).doesNotContain("DEMO-ELEC-PHONE");
		assertThat(DemoSeedMarkers.categorySlugs()).contains("food-fruits-vegetables");
		assertThat(DemoSeedMarkers.categorySlugs()).doesNotContain("demo-electronics");
		assertThat(DemoSeedMarkers.retiredProductSkus()).contains("DEMO-ELEC-PHONE", "DEMO-HOME-MUG");
		assertThat(DemoSeedMarkers.retiredCategorySlugs()).contains("demo-electronics", "demo-archive");
		assertThat(DemoSeedMarkers.resetProductSkus()).contains("FOOD-FRUIT-BANANA-001", "DEMO-ELEC-PHONE");
	}
}
