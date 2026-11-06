package com.aptech.aptech4.common.locale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContentLocalizationTest {

	@Test
	void resolveNamePrefersNonBlankTranslation() {
		assertThat(ContentLocalization.resolveName("English", "Tiếng Việt")).isEqualTo("English");
		assertThat(ContentLocalization.resolveName("  ", "Tiếng Việt")).isEqualTo("Tiếng Việt");
		assertThat(ContentLocalization.resolveName(null, "Tiếng Việt")).isEqualTo("Tiếng Việt");
	}

	@Test
	void resolveDescriptionFallsBackOnlyWhenRowMissing() {
		assertThat(ContentLocalization.resolveDescription(false, null, "base")).isEqualTo("base");
		assertThat(ContentLocalization.resolveDescription(true, null, "base")).isNull();
		assertThat(ContentLocalization.resolveDescription(true, "en desc", "base")).isEqualTo("en desc");
	}

	@Test
	void resolveKeepsCanonicalNameWhenTranslationBlank() {
		LocalizedText text = ContentLocalization.resolve(true, "", null, "Tên VI", "Mô tả VI");
		assertThat(text.name()).isEqualTo("Tên VI");
		assertThat(text.description()).isNull();
	}
}
