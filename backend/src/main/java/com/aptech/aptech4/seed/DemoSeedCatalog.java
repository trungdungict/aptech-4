package com.aptech.aptech4.seed;

import java.math.BigDecimal;
import java.util.List;

/**
 * Managed demo fixtures. Natural keys (slug / SKU / email) own upsert scope — seed never deletes other rows.
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

	public static final List<CategoryFixture> CATEGORIES = List.of(
			new CategoryFixture("electronics", "Electronics", "demo-electronics", "Phones, audio, accessories", true),
			new CategoryFixture("books", "Books", "demo-books", "Paperbacks and study guides", true),
			new CategoryFixture("home", "Home & Living", "demo-home", "Kitchen and desk essentials", true),
			new CategoryFixture("archive", "Archive (Inactive)", "demo-archive", "Hidden from public catalog", false)
	);

	public static final List<ProductFixture> PRODUCTS = List.of(
			new ProductFixture("phone", "electronics", "DEMO-ELEC-PHONE", "Demo Smartphone X", "Flagship demo phone",
					new BigDecimal("12990000.00"), true, 25),
			new ProductFixture("earbuds", "electronics", "DEMO-ELEC-EARBUDS", "Demo Wireless Earbuds", "Noise-cancel demo buds",
					new BigDecimal("1590000.00"), true, 80),
			new ProductFixture("charger", "electronics", "DEMO-ELEC-CHARGER", "Demo USB-C Charger 65W", "GaN charger",
					new BigDecimal("490000.00"), true, 120),
			new ProductFixture("laptop-stand", "electronics", "DEMO-ELEC-STAND", "Demo Laptop Stand", "Aluminum stand",
					new BigDecimal("750000.00"), true, 40),
			new ProductFixture("java-book", "books", "DEMO-BOOK-JAVA", "Effective Java (Demo)", "Study guide edition",
					new BigDecimal("450000.00"), true, 30),
			new ProductFixture("sql-book", "books", "DEMO-BOOK-SQL", "SQL Pocket Guide (Demo)", "Quick reference",
					new BigDecimal("220000.00"), true, 50),
			new ProductFixture("algo-book", "books", "DEMO-BOOK-ALGO", "Algorithms Unlocked (Demo)", "CS fundamentals",
					new BigDecimal("380000.00"), true, 15),
			new ProductFixture("mug", "home", "DEMO-HOME-MUG", "Demo Ceramic Mug", "350ml white mug",
					new BigDecimal("89000.00"), true, 200),
			new ProductFixture("lamp", "home", "DEMO-HOME-LAMP", "Demo Desk Lamp", "LED arm lamp",
					new BigDecimal("320000.00"), true, 35),
			new ProductFixture("out-of-stock", "home", "DEMO-HOME-OOS", "Demo Sold-Out Notebook", "For insufficient-stock demos",
					new BigDecimal("55000.00"), true, 0),
			new ProductFixture("inactive-phone", "electronics", "DEMO-ELEC-OLD", "Demo Discontinued Phone", "Inactive product",
					new BigDecimal("9990000.00"), false, 5),
			new ProductFixture("archive-item", "archive", "DEMO-ARCH-01", "Demo Archived Gadget", "In inactive category",
					new BigDecimal("100000.00"), true, 10)
	);
}
