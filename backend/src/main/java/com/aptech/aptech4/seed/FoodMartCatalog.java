package com.aptech.aptech4.seed;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Loads classpath {@code db/demo/foodmart-catalog.json} — backend source of truth for FoodMart
 * grocery fixtures (SKU / slug / inventory). Frontend keeps only SKU→image presentation maps.
 */
public final class FoodMartCatalog {

	private static final String RESOURCE = "/db/demo/foodmart-catalog.json";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static volatile Document cached;

	private FoodMartCatalog() {
	}

	public static Document load() {
		Document local = cached;
		if (local != null) {
			return local;
		}
		synchronized (FoodMartCatalog.class) {
			if (cached == null) {
				cached = read();
			}
			return cached;
		}
	}

	public static List<DemoSeedCatalog.CategoryFixture> categories() {
		List<DemoSeedCatalog.CategoryFixture> list = new ArrayList<>();
		for (CategoryRow row : load().categories()) {
			list.add(new DemoSeedCatalog.CategoryFixture(
					row.key(),
					row.name(),
					row.slug(),
					row.description(),
					row.active()
			));
		}
		return List.copyOf(list);
	}

	public static List<DemoSeedCatalog.ProductFixture> products() {
		List<DemoSeedCatalog.ProductFixture> list = new ArrayList<>();
		for (ProductRow row : load().products()) {
			list.add(new DemoSeedCatalog.ProductFixture(
					row.key(),
					row.categoryKey(),
					row.sku(),
					row.name(),
					row.description(),
					new BigDecimal(row.unitPrice()),
					row.active(),
					row.onHand()
			));
		}
		return List.copyOf(list);
	}

	private static Document read() {
		try (InputStream in = FoodMartCatalog.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("Missing classpath resource " + RESOURCE);
			}
			Document doc = MAPPER.readValue(in, Document.class);
			if (doc.categories() == null || doc.categories().isEmpty()) {
				throw new IllegalStateException("FoodMart catalog has no categories");
			}
			if (doc.products() == null || doc.products().size() < 40 || doc.products().size() > 50) {
				throw new IllegalStateException(
						"FoodMart catalog must contain 40–50 products, found "
								+ (doc.products() == null ? 0 : doc.products().size())
				);
			}
			return doc;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to load " + RESOURCE, ex);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Document(List<CategoryRow> categories, List<ProductRow> products) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CategoryRow(String key, String name, String slug, String description, boolean active) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ProductRow(
			String key,
			String categoryKey,
			String sku,
			String name,
			String description,
			String unitPrice,
			boolean active,
			int onHand
	) {
	}
}
