package com.aptech.aptech4.common.locale;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

@Configuration
public class LocaleConfig {

	@Bean
	MessageSource messageSource() {
		ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
		source.setBasename("classpath:i18n/messages");
		source.setDefaultEncoding("UTF-8");
		source.setFallbackToSystemLocale(false);
		source.setDefaultLocale(AppLocale.DEFAULT.toJavaLocale());
		return source;
	}

	@Bean
	ApiMessageResolver apiMessageResolver(MessageSource messageSource) {
		return new ApiMessageResolver(messageSource);
	}
}
