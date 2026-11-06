package com.aptech.aptech4.common.locale;

import java.util.Locale;

/**
 * Supported application locales for API content and (later) messages.
 * Default and fallback is {@link #VI}.
 */
public enum AppLocale {
	VI("vi", Locale.forLanguageTag("vi")),
	EN("en", Locale.forLanguageTag("en"));

	public static final AppLocale DEFAULT = VI;
	public static final AppLocale FALLBACK = VI;

	private final String code;
	private final Locale javaLocale;

	AppLocale(String code, Locale javaLocale) {
		this.code = code;
		this.javaLocale = javaLocale;
	}

	/** BCP 47 primary language subtag used on the wire ({@code vi} / {@code en}). */
	public String code() {
		return code;
	}

	public Locale toJavaLocale() {
		return javaLocale;
	}

	/**
	 * Resolves a primary language subtag (already lower-cased, no region) to a supported locale.
	 *
	 * @return matching locale, or {@code null} if unsupported
	 */
	public static AppLocale fromLanguageCode(String primarySubtag) {
		if (primarySubtag == null || primarySubtag.isBlank()) {
			return null;
		}
		return switch (primarySubtag) {
			case "vi" -> VI;
			case "en" -> EN;
			default -> null;
		};
	}

	public static AppLocale fromCodeOrDefault(String code) {
		AppLocale match = fromLanguageCode(code == null ? null : code.trim().toLowerCase(Locale.ROOT));
		return match == null ? DEFAULT : match;
	}
}
