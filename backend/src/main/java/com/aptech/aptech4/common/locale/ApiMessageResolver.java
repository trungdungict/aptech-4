package com.aptech.aptech4.common.locale;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

/**
 * Resolves API human-readable messages for the current {@link RequestLocaleHolder} locale.
 */
public class ApiMessageResolver {

	private final MessageSource messageSource;

	public ApiMessageResolver(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	public String get(String code, Object... args) {
		Locale locale = RequestLocaleHolder.get().toJavaLocale();
		try {
			return messageSource.getMessage(code, args, locale);
		}
		catch (NoSuchMessageException ex) {
			try {
				return messageSource.getMessage(code, args, AppLocale.FALLBACK.toJavaLocale());
			}
			catch (NoSuchMessageException nested) {
				return code;
			}
		}
	}
}
