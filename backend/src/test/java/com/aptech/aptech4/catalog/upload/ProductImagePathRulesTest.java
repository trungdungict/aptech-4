package com.aptech.aptech4.catalog.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.aptech.aptech4.common.api.ApiException;

class ProductImagePathRulesTest {

	@Test
	void acceptsSafeUuidRelativePath() {
		String path = "/uploads/products/9d1f7e4b-1111-4111-8111-111111111111.png";
		assertThat(ProductImagePathRules.normalizeOrNull(path)).isEqualTo(path);
	}

	@Test
	void blankClearsImage() {
		assertThat(ProductImagePathRules.normalizeOrNull(null)).isNull();
		assertThat(ProductImagePathRules.normalizeOrNull("  ")).isNull();
	}

	@Test
	void rejectsPathTraversalAndAbsoluteUrls() {
		assertThatThrownBy(() -> ProductImagePathRules.normalizeOrNull("/uploads/products/../secret.png"))
				.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> ProductImagePathRules.normalizeOrNull("/uploads/products/..\\evil.png"))
				.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> ProductImagePathRules.normalizeOrNull("https://evil.example/x.png"))
				.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> ProductImagePathRules.normalizeOrNull("/etc/passwd"))
				.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> ProductImagePathRules.normalizeOrNull("/uploads/products/not-a-uuid.png"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void normalizesJpegExtension() {
		String path = "/uploads/products/9d1f7e4b-1111-4111-8111-111111111111.jpeg";
		assertThat(ProductImagePathRules.normalizeOrNull(path))
				.isEqualTo("/uploads/products/9d1f7e4b-1111-4111-8111-111111111111.jpg");
	}
}
