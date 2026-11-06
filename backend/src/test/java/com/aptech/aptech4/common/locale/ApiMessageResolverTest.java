package com.aptech.aptech4.common.locale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

class ApiMessageResolverTest {

	private ApiMessageResolver resolver;

	@BeforeEach
	void setUp() {
		ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
		source.setBasename("classpath:i18n/messages");
		source.setDefaultEncoding("UTF-8");
		source.setFallbackToSystemLocale(false);
		source.setDefaultLocale(AppLocale.DEFAULT.toJavaLocale());
		resolver = new ApiMessageResolver(source);
	}

	@AfterEach
	void tearDown() {
		RequestLocaleHolder.clear();
	}

	@Test
	void resolvesVietnameseByDefault() {
		RequestLocaleHolder.clear();
		assertThat(resolver.get("api.error.not_found.product")).isEqualTo("Không tìm thấy sản phẩm");
	}

	@Test
	void resolvesEnglishWhenLocaleEn() {
		RequestLocaleHolder.set(AppLocale.EN);
		assertThat(resolver.get("api.error.not_found.product")).isEqualTo("Product not found");
	}

	@Test
	void keepsMachineReadableKeysStableAcrossLocales() {
		RequestLocaleHolder.set(AppLocale.VI);
		String vi = resolver.get("api.error.bad_request.locale");
		RequestLocaleHolder.set(AppLocale.EN);
		String en = resolver.get("api.error.bad_request.locale");
		assertThat(vi).isNotEqualTo(en);
		assertThat(vi).contains("vi");
		assertThat(en).contains("vi");
	}
}
