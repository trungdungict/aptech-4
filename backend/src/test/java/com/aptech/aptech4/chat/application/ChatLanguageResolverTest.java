package com.aptech.aptech4.chat.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.aptech.aptech4.chat.api.ChatLanguage;

class ChatLanguageResolverTest {

	private final ChatLanguageResolver resolver = new ChatLanguageResolver();

	@Test
	void defaultsToEnglish() {
		assertThat(resolver.detect("Hello")).isEqualTo(ChatLanguage.EN);
		assertThat(resolver.detect(null)).isEqualTo(ChatLanguage.EN);
	}

	@Test
	void detectsVietnameseDiacritics() {
		assertThat(resolver.detect("Đơn hàng gần nhất của tôi")).isEqualTo(ChatLanguage.VI);
	}

	@Test
	void explicitLanguageWinsOverDetection() {
		assertThat(resolver.resolve("EN", "Đơn hàng của tôi", null)).isEqualTo(ChatLanguage.EN);
		assertThat(resolver.resolve("VI", "Show my orders", null)).isEqualTo(ChatLanguage.VI);
	}

	@Test
	void conversationPreferenceUsedWhenNoExplicit() {
		assertThat(resolver.resolve(null, "hello", ChatLanguage.VI)).isEqualTo(ChatLanguage.VI);
	}

	@Test
	void nullPreferenceFallsBackToDetection() {
		assertThat(resolver.resolve(null, "Đơn hàng gần nhất của tôi", null)).isEqualTo(ChatLanguage.VI);
		assertThat(resolver.resolve(null, "Hello", null)).isEqualTo(ChatLanguage.EN);
	}
}
