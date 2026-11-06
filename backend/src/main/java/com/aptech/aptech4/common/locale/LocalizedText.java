package com.aptech.aptech4.common.locale;

/**
 * Resolved display text: prefer locale-specific translation, else canonical base (VI fallback column).
 */
public record LocalizedText(String name, String description) {

	public static LocalizedText of(String name, String description) {
		return new LocalizedText(name, description);
	}
}
