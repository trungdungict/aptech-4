package com.aptech.aptech4.common.locale;

/**
 * Pure fallback helpers for localized display fields (no I/O).
 */
public final class ContentLocalization {

	private ContentLocalization() {
	}

	/**
	 * Prefer translated name when non-blank; otherwise canonical base name (never blank if base is set).
	 */
	public static String resolveName(String translatedName, String canonicalName) {
		if (translatedName != null && !translatedName.isBlank()) {
			return translatedName;
		}
		return canonicalName;
	}

	/**
	 * Prefer translated description when the translation row exists with a non-null value
	 * (including blank→null already normalized). If no translation row, use canonical.
	 * If translation row exists with null description, keep null (optional field).
	 */
	public static String resolveDescription(
			boolean translationPresent,
			String translatedDescription,
			String canonicalDescription
	) {
		if (!translationPresent) {
			return canonicalDescription;
		}
		return translatedDescription;
	}

	public static LocalizedText resolve(
			boolean translationPresent,
			String translatedName,
			String translatedDescription,
			String canonicalName,
			String canonicalDescription
	) {
		return LocalizedText.of(
				resolveName(translatedName, canonicalName),
				resolveDescription(translationPresent, translatedDescription, canonicalDescription)
		);
	}
}
