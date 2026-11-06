package com.aptech.aptech4.common.locale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppLocaleTest {

	@Test
	void defaultAndFallbackAreVietnamese() {
		assertThat(AppLocale.DEFAULT).isEqualTo(AppLocale.VI);
		assertThat(AppLocale.FALLBACK).isEqualTo(AppLocale.VI);
		assertThat(AppLocale.VI.code()).isEqualTo("vi");
		assertThat(AppLocale.EN.code()).isEqualTo("en");
	}

	@Test
	void fromLanguageCodeMapsSupportedOnly() {
		assertThat(AppLocale.fromLanguageCode("vi")).isEqualTo(AppLocale.VI);
		assertThat(AppLocale.fromLanguageCode("en")).isEqualTo(AppLocale.EN);
		assertThat(AppLocale.fromLanguageCode("fr")).isNull();
		assertThat(AppLocale.fromLanguageCode(null)).isNull();
	}

	@Test
	void fromCodeOrDefaultFallsBack() {
		assertThat(AppLocale.fromCodeOrDefault("EN")).isEqualTo(AppLocale.EN);
		assertThat(AppLocale.fromCodeOrDefault("nope")).isEqualTo(AppLocale.VI);
	}

	@Test
	void javaLocaleLanguageMatchesCode() {
		assertThat(AppLocale.VI.toJavaLocale().getLanguage()).isEqualTo("vi");
		assertThat(AppLocale.EN.toJavaLocale().getLanguage()).isEqualTo("en");
	}
}
