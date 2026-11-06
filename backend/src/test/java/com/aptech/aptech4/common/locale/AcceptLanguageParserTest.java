package com.aptech.aptech4.common.locale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AcceptLanguageParserTest {

	@Test
	void missingHeaderDefaultsToVietnamese() {
		assertThat(AcceptLanguageParser.resolve(null)).isEqualTo(AppLocale.VI);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "\t" })
	void blankHeaderDefaultsToVietnamese(String header) {
		assertThat(AcceptLanguageParser.resolve(header)).isEqualTo(AppLocale.VI);
	}

	@Test
	void resolvesVietnameseAndEnglish() {
		assertThat(AcceptLanguageParser.resolve("vi")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("en")).isEqualTo(AppLocale.EN);
	}

	@Test
	void normalizesCase() {
		assertThat(AcceptLanguageParser.resolve("VI")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("En")).isEqualTo(AppLocale.EN);
		assertThat(AcceptLanguageParser.resolve("EN-us")).isEqualTo(AppLocale.EN);
	}

	@Test
	void resolvesRegionalTagsToPrimary() {
		assertThat(AcceptLanguageParser.resolve("en-US")).isEqualTo(AppLocale.EN);
		assertThat(AcceptLanguageParser.resolve("en-GB")).isEqualTo(AppLocale.EN);
		assertThat(AcceptLanguageParser.resolve("vi-VN")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("vi_VN")).isEqualTo(AppLocale.VI);
	}

	@Test
	void selectsHighestQualitySupportedLanguage() {
		assertThat(AcceptLanguageParser.resolve("fr;q=0.9,en;q=0.8,vi;q=0.7")).isEqualTo(AppLocale.EN);
		assertThat(AcceptLanguageParser.resolve("en;q=0.4,vi;q=0.8")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("en-US,en;q=0.9,vi;q=0.8")).isEqualTo(AppLocale.EN);
	}

	@Test
	void equalQualityKeepsHeaderOrder() {
		assertThat(AcceptLanguageParser.resolve("vi;q=0.5,en;q=0.5")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("en;q=0.5,vi;q=0.5")).isEqualTo(AppLocale.EN);
	}

	@Test
	void unsupportedLanguageFallsBackToVietnamese() {
		assertThat(AcceptLanguageParser.resolve("fr")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("ja-JP")).isEqualTo(AppLocale.VI);
	}

	@Test
	void allUnsupportedFallsBackToVietnamese() {
		assertThat(AcceptLanguageParser.resolve("fr,de;q=0.8,ja;q=0.2")).isEqualTo(AppLocale.VI);
	}

	@Test
	void skipsUnsupportedThenPicksSupported() {
		assertThat(AcceptLanguageParser.resolve("fr,en")).isEqualTo(AppLocale.EN);
		assertThat(AcceptLanguageParser.resolve("de;q=1,vi;q=0.1")).isEqualTo(AppLocale.VI);
	}

	@Test
	void malformedEntriesAreIgnored() {
		assertThat(AcceptLanguageParser.resolve(",,,")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("!!!")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("en;,vi")).isEqualTo(AppLocale.EN);
		assertThat(AcceptLanguageParser.resolve(" ;q=1, vi")).isEqualTo(AppLocale.VI);
	}

	@Test
	void invalidOrOutOfRangeQualityIgnoresEntry() {
		assertThat(AcceptLanguageParser.resolve("en;q=abc,vi")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("en;q=1.5,vi")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("en;q=-0.1,vi")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("en;q=,vi")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("vi;q=2")).isEqualTo(AppLocale.VI);
	}

	@Test
	void zeroQualityMeansNotAcceptable() {
		assertThat(AcceptLanguageParser.resolve("en;q=0")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("en;q=0,vi;q=0.5")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("vi;q=0,en;q=0.2")).isEqualTo(AppLocale.EN);
	}

	@Test
	void missingQualityDefaultsToOne() {
		assertThat(AcceptLanguageParser.resolve("vi")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("fr,en")).isEqualTo(AppLocale.EN);
	}

	@Test
	void wildcardAloneFallsBackToVietnamese() {
		assertThat(AcceptLanguageParser.resolve("*")).isEqualTo(AppLocale.VI);
		assertThat(AcceptLanguageParser.resolve("*;q=0.8")).isEqualTo(AppLocale.VI);
	}

	@Test
	void wildcardDoesNotBlockLowerQualitySupportedLanguage() {
		// * has implicit q=1.0; policy skips wildcard and selects en at q=0.5.
		assertThat(AcceptLanguageParser.resolve("*,en;q=0.5")).isEqualTo(AppLocale.EN);
		assertThat(AcceptLanguageParser.resolve("*;q=1,vi;q=0.1")).isEqualTo(AppLocale.VI);
	}

	@Test
	void wildcardWithOnlyUnsupportedStillDefaultsToVietnamese() {
		assertThat(AcceptLanguageParser.resolve("fr,*,de;q=0.2")).isEqualTo(AppLocale.VI);
	}
}
