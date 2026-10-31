package com.aptech.aptech4.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import com.aptech.aptech4.catalog.upload.ProductImagePathRules;
import com.aptech.aptech4.catalog.upload.ProductImageProperties;

class FoodMartProductImagesTest {

	@TempDir
	Path tempDir;

	private FoodMartProductImages images;

	@BeforeEach
	void setUp() {
		ProductImageProperties props = new ProductImageProperties();
		props.setUploadDir(tempDir.resolve("products").toString());
		props.setPromotionUploadDir(tempDir.resolve("promotions").toString());
		props.validate();
		ObjectProvider<ProductImageProperties> provider = new ObjectProvider<>() {
			@Override
			public ProductImageProperties getObject() {
				return props;
			}

			@Override
			public ProductImageProperties getObject(Object... args) {
				return props;
			}

			@Override
			public ProductImageProperties getIfAvailable() {
				return props;
			}

			@Override
			public ProductImageProperties getIfUnique() {
				return props;
			}
		};
		images = new FoodMartProductImages(provider);
	}

	@Test
	void everyFoodMartSkuMapsToAValidUploadPathAndWritesBytes() throws Exception {
		assertThat(images.skuAssetMap()).hasSize(FoodMartCatalog.products().size());

		Set<String> paths = new HashSet<>();
		for (var product : FoodMartCatalog.products()) {
			String first = images.ensurePersistedImageUrl(product.sku());
			assertThat(ProductImagePathRules.normalizeOrNull(first)).isEqualTo(first);
			assertThat(first).startsWith("/uploads/products/");
			assertThat(paths.add(first)).isTrue();

			String second = images.ensurePersistedImageUrl(product.sku());
			assertThat(second).isEqualTo(first);

			String filename = first.substring(first.lastIndexOf('/') + 1);
			Path file = tempDir.resolve("products").resolve(filename);
			assertThat(Files.isRegularFile(file)).isTrue();
			assertThat(Files.size(file)).isGreaterThan(0);
		}
	}

	@Test
	void deterministicIdIsStablePerSku() {
		assertThat(FoodMartProductImages.deterministicId("FOOD-FRUIT-BANANA-001"))
				.isEqualTo(FoodMartProductImages.deterministicId("FOOD-FRUIT-BANANA-001"));
		assertThat(FoodMartProductImages.deterministicId("FOOD-FRUIT-BANANA-001"))
				.isNotEqualTo(FoodMartProductImages.deterministicId("FOOD-FRUIT-APPLE-001"));
	}
}
